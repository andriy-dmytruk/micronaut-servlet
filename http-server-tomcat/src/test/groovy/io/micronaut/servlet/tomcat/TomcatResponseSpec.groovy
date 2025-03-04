package io.micronaut.servlet.tomcat

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject

@Property(name = 'spec.name', value = 'TomcatResponseSpec')
@MicronautTest
class TomcatResponseSpec extends Specification {
    @Inject
    @Client("/")
    RxHttpClient client

    @Issue("https://github.com/micronaut-projects/micronaut-servlet/issues/177")
    void 'verify controller with HttpResponse as returned type is handled correctly'() {
        when:
        def response = client.exchange(HttpRequest.POST("/response/test/bar", "content"), String).blockingFirst()

        then:
        response.status() == HttpStatus.OK
        response.body() == null
    }

    @Requires(property = 'spec.name', value = 'TomcatResponseSpec')
    @Controller("/response/test")
    static class FooController {

        @Post("/bar")
        HttpResponse bar(@Body String json) {
            HttpResponse.ok()
        }
    }
}
