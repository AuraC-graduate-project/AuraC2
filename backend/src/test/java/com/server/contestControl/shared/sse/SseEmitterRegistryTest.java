package com.server.contestControl.shared.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmitterRegistryTest {

    @Test
    void safeBroadcastSendDropsEmitterWithoutCompletingAfterSendFailure() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        TestSseEmitter emitter = TestSseEmitter.failingWith(new IOException("broken pipe"));
        registry.register(emitter);

        boolean sent = registry.safeBroadcastSend(emitter, pingEvent());

        assertThat(sent).isFalse();
        assertThat(registry.activeCount()).isZero();
        assertThat(emitter.completeCalled).isFalse();
    }

    @Test
    void safeTargetedSendDropsEmitterWithoutCompletingAfterSendFailure() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        TestSseEmitter emitter = TestSseEmitter.failingWith(
                new IllegalStateException("Response not usable after response errors"));
        registry.register(1L, emitter);

        boolean sent = registry.safeTargetedSend(1L, emitter, pingEvent());

        assertThat(sent).isFalse();
        assertThat(registry.activeCount()).isZero();
        assertThat(emitter.completeCalled).isFalse();
    }

    @Test
    void timeoutRemovesTargetedEmitterEvenWhenCompletionFails() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        TestSseEmitter emitter = TestSseEmitter.working();
        emitter.failCompletion = true;
        registry.register(1L, emitter);

        assertThatCode(() -> emitter.timeoutCallback.run()).doesNotThrowAnyException();

        assertThat(registry.activeCount()).isZero();
        assertThat(emitter.completeCalled).isTrue();
    }

    @Test
    void keepAliveReachesBroadcastAndTargetedEmitters() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        TestSseEmitter broadcastEmitter = TestSseEmitter.working();
        TestSseEmitter targetedEmitter = TestSseEmitter.working();
        registry.register(broadcastEmitter);
        registry.register(1L, targetedEmitter);

        registry.keepAliveAll(pingEvent());

        assertThat(broadcastEmitter.sendCount).isEqualTo(1);
        assertThat(targetedEmitter.sendCount).isEqualTo(1);
        assertThat(registry.activeCount()).isEqualTo(2);
    }

    private SseEmitter.SseEventBuilder pingEvent() {
        return SseEmitter.event().name("ping").data("ping");
    }

    private static class TestSseEmitter extends SseEmitter {
        private final Exception sendFailure;
        private boolean completeCalled;
        private boolean failCompletion;
        private Runnable timeoutCallback;
        private int sendCount;

        private TestSseEmitter(Exception sendFailure) {
            this.sendFailure = sendFailure;
        }

        static TestSseEmitter working() {
            return new TestSseEmitter(null);
        }

        static TestSseEmitter failingWith(Exception sendFailure) {
            return new TestSseEmitter(sendFailure);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (sendFailure instanceof IOException ioException) {
                throw ioException;
            }
            if (sendFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            sendCount++;
        }

        @Override
        public void complete() {
            completeCalled = true;
            if (failCompletion) {
                throw new IllegalStateException("already unusable");
            }
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
        }

        @Override
        public void onCompletion(Runnable callback) {
        }
    }
}
