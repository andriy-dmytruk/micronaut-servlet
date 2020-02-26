package io.micronaut.servlet.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequestWrapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal class that represents an {@link ServletHttpRequest} that includes the body already decoded.
 *
 * @param <N> The native request type
 * @param <B> The body type
 * @author graemerocher
 * @since 2.0.0
 */
@Internal
final class ServletRequestAndBody<N, B> extends HttpRequestWrapper<B> implements ServletHttpRequest<N, B> {

    private final B body;

    /**
     * @param delegate The Http Request
     * @param body     The body, never null
     */
    ServletRequestAndBody(ServletHttpRequest<N, B> delegate, B body) {
        super(delegate);
        this.body = Objects.requireNonNull(body, "Body cannot be null");
    }

    @Override
    public boolean isAsyncSupported() {
        return ((ServletHttpRequest<N, B>) getDelegate()).isAsyncSupported();
    }

    @Override
    public Optional<B> getBody() {
        return Optional.of(body);
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        return ConversionService.SHARED.convert(body, type);
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return ConversionService.SHARED.convert(body, type);
    }

    @Override
    public InputStream getInputStream() {
        throw new IllegalStateException("Body already read");
    }

    @Override
    public BufferedReader getReader() {
        throw new IllegalStateException("Body already read");
    }

    @Override
    public N getNativeRequest() {
        return ((ServletHttpRequest<N, B>) getDelegate()).getNativeRequest();
    }
}
