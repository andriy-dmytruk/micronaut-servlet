
package io.micronaut.servlet.jetty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.core.io.buffer.ReferenceCounted
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.core.annotation.Nullable
import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

/**
 * @author Jesper Steen Møller
 * @since 1.0
 */
class JettyStreamSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test that the server can return a header value to us"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        HttpResponse<String> response = myClient.echoWithHeaders(2, "Hello!")
        then:
        response.status == HttpStatus.OK
        response.header('X-MyHeader') == "42"
        response.body() == "Hello!Hello!"
    }

    void "test that the server can return a header value to us with a single"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        HttpResponse<String> response = myClient.echoWithHeadersSingle( "Hello!")
        then:
        response.status == HttpStatus.OK
        response.header('X-MyHeader') == "42"
        response.body() == "Hello!"
    }

    void "test send and receive parameter"() {
        given:
        int n = 800// This can be as high as 806596 - if any higher, it will overflow the default aggregator buffer size
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        String result = myClient.echoAsString(n, "Hello, World!")
        then:
        result.length() == n * 13 // Should be less than 10485760
    }

    void "test receive client using ByteBuffer"() {
        given:
        int n = 42376 // This may be higher than 806596, but the test takes forever, then.
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        Flowable<ByteBuffer> responseFlowable = myClient.echoAsByteBuffers(n, "Hello, World!")
        int sum = 0
        CountDownLatch latch = new CountDownLatch(1)
        responseFlowable.subscribe(new Subscriber<ByteBuffer<?>>() {
            private Subscription s

            @Override
            void onSubscribe(Subscription s) {
                this.s = s
                s.request(1)
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                sum += byteBuffer.toByteArray().count('!')
                s.request(1)
            }

            @Override
            void onError(Throwable t) {
                latch.countDown()
            }

            @Override
            void onComplete() {
                latch.countDown()
            }
        })
        latch.await()

        then:
        sum == n
    }


    void "test that the client is unable to convert bytes to elephants"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        Elephant _ = myClient.echoAsElephant(42, "Hello, big grey animal!").blockingFirst()
        then:
        def ex = thrown(ConfigurationException)
        ex.message == 'Cannot create the generated HTTP client\'s required return type, since no TypeConverter from ByteBuffer to class io.micronaut.servlet.jetty.JettyStreamSpec$Elephant is registered'
    }

    @Unroll
    void "JSON is still just text (variation #n)"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        expect:
        myClient.someJson(n) == '{"key":"value"}'
        where:
        n << [1, 2, 3]
    }

    @Unroll
    void "JSON can still be streamed using Flowable as container"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        expect:
        myClient.someJsonCollection() == '[{"x":1},{"x":2}]'
    }

    @Client('/stream')
    static interface StreamEchoClient {
        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        String echoAsString(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Flowable<ByteBuffer<?>> echoAsByteBuffers(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Flowable<Elephant> echoAsElephant(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echoWithHeaders{?n,data}", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> echoWithHeaders(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echoWithHeadersSingle{?data}", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> echoWithHeadersSingle(@QueryValue @Nullable String data);

        @Get(value = "/someJson{n}", consumes = MediaType.APPLICATION_JSON)
        String someJson(int n);

        @Get(value = "/someJsonCollection", consumes = MediaType.APPLICATION_JSON)
        String someJsonCollection();
    }

    static class Elephant {
        int trunkLength
        int earSize
    }

    @Controller('/stream')
    static class StreamEchoController {

        @Inject ByteBufferFactory<?, ?> bufferFactory

        @Get(value = "/echo{?n,data}", produces = MediaType.TEXT_PLAIN)
        Flowable<byte[]> postStream(@QueryValue @Nullable int n,  @QueryValue @Nullable String data) {
            return Flowable.just(data.getBytes(StandardCharsets.UTF_8)).repeat(n)
        }

        @Get(value = "/echoWithHeaders{?n,data}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<Flowable<byte[]>> echoWithHeaders(@QueryValue @Nullable int n, @QueryValue @Nullable String data) {
            return HttpResponse.ok(Flowable.just(data.getBytes(StandardCharsets.UTF_8)).repeat(n)).header("X-MyHeader", "42")
        }

        @Get(value = "/echoWithHeadersSingle{?data}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<Single<byte[]>> echoWithHeadersSingle(@QueryValue @Nullable String data) {
            return HttpResponse.ok(Single.just(data.getBytes(StandardCharsets.UTF_8))).header("X-MyHeader", "42")
        }

        @Get(value = "/someJson1", produces = MediaType.APPLICATION_JSON)
        Flowable<byte[]> someJson1() {
            return Flowable.just('{"key":"value"}'.getBytes(StandardCharsets.UTF_8))
        }

        @Get(value = "/someJson2", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Flowable<byte[]>> someJson2() {
            return HttpResponse.ok(Flowable.just('{"key":"value"}'.getBytes(StandardCharsets.UTF_8)))
        }

        @Get(value = "/someJson3", produces = MediaType.APPLICATION_JSON)
        Flowable<ByteBuffer> someJson3() {
            return Flowable.just(byteBuf('{"key":'), byteBuf('"value"}'))
        }

        @Get(value = "/someJsonCollection", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Flowable<String>> someJsonCollection() {
            return HttpResponse.ok(Flowable.just('{"x":1}','{"x":2}'))
        }

        private ByteBuffer byteBuf(String s) {
            bufferFactory.wrap(s.getBytes(StandardCharsets.UTF_8))
        }

    }

}
