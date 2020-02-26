package io.micronaut.servlet.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.Readable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.exceptions.HttpStatusException;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Allows binding the body from a {@link ServletHttpRequest}.
 *
 * @param <T> The body type
 * @author graemerocher
 * @since 2.0.0
 */
@Internal
final class ServletBodyBinder<T> extends DefaultBodyAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<Body, T> {
    private final MediaTypeCodecRegistry mediaTypeCodeRegistry;

    /**
     * Default constructor.
     * @param conversionService The conversion service
     * @param mediaTypeCodecRegistry The codec registry
     */
    ServletBodyBinder(
            ConversionService<?> conversionService,
            MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        super(conversionService);
        this.mediaTypeCodeRegistry = mediaTypeCodecRegistry;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        final Argument<T> argument = context.getArgument();
        final Class<T> type = argument.getType();
        if (source instanceof ServletHttpRequest) {
            ServletHttpRequest<?, ?> servletHttpRequest = (ServletHttpRequest<?, ?>) source;
            if (Readable.class.isAssignableFrom(type)) {
                Readable readable = new Readable() {
                    @Override
                    public Reader asReader() throws IOException {
                        return servletHttpRequest.getReader();
                    }

                    @Nonnull
                    @Override
                    public InputStream asInputStream() throws IOException {
                        return servletHttpRequest.getInputStream();
                    }

                    @Override
                    public boolean exists() {
                        return true;
                    }

                    @Nonnull
                    @Override
                    public String getName() {
                        return servletHttpRequest.getPath();
                    }
                };
                return () -> (Optional<T>) Optional.of(readable);
            } else if (CharSequence.class.isAssignableFrom(type)) {
                try (InputStream inputStream = servletHttpRequest.getInputStream()) {
                    final String content = IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream, source.getCharacterEncoding())));
                    return () -> (Optional<T>) Optional.of(content);
                } catch (IOException e) {
                    return new BindingResult<T>() {
                        @Override
                        public Optional<T> getValue() {
                            return Optional.empty();
                        }

                        @Override
                        public List<ConversionError> getConversionErrors() {
                            return Collections.singletonList(
                                    () -> e
                            );
                        }
                    };
                }

            } else {
                final MediaType mediaType = source.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                final MediaTypeCodec codec = mediaTypeCodeRegistry
                        .findCodec(mediaType, type)
                        .orElse(null);

                if (codec != null) {

                    try (InputStream inputStream = servletHttpRequest.getInputStream()) {
                        if (Publishers.isConvertibleToPublisher(type)) {
                            final Argument<?> typeArg = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                            if (Publishers.isSingle(type)) {
                                T content = (T) codec.decode(typeArg, inputStream);
                                final Publisher<T> publisher = Publishers.just(content);
                                final T converted = conversionService.convertRequired(publisher, type);
                                return () -> Optional.of(converted);
                            } else {
                                final Argument<? extends List<?>> containerType = Argument.listOf(typeArg.getType());
                                T content = (T) codec.decode(containerType, inputStream);
                                final Flowable flowable = Flowable.fromIterable((Iterable) content);
                                final T converted = conversionService.convertRequired(flowable, type);
                                return () -> Optional.of(converted);
                            }
                        } else {
                            T content = codec.decode(argument, inputStream);
                            return () -> Optional.of(content);
                        }
                    } catch (CodecException | IOException e) {
                        throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Unable to decode request body: " + e.getMessage());
                    }
                }

            }
        }
        return super.bind(context, source);
    }
}
