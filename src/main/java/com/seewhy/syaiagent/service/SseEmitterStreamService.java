package com.seewhy.syaiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterStreamService {

    private static final long DEFAULT_TIMEOUT_MILLIS = 180_000L;

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public SseEmitter stream(String sessionKey, String logName, Flux<String> source) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);

        SseEmitter existing = activeEmitters.putIfAbsent(sessionKey, emitter);
        if (existing != null) {
            completeWithMessage(emitter, "[错误] 无法建立新的会话，已有活动会话");
            return emitter;
        }

        Disposable disposable = source
                .doOnCancel(() -> log.info("{} SSE cancelled for {}", logName, sessionKey))
                .onErrorContinue((err, obj) ->
                        log.debug("{} SSE error ignored for {}: {}", logName, sessionKey, err.toString()))
                .subscribe(chunk -> sendChunk(emitter, sessionKey, chunk), err -> {
                    log.debug("{} SSE stream error for {}: {}", logName, sessionKey, err.toString());
                    complete(emitter);
                }, () -> complete(emitter));

        emitter.onCompletion(() -> cleanup(sessionKey, disposable, logName, "completed"));
        emitter.onTimeout(() -> {
            cleanup(sessionKey, disposable, logName, "timeout");
            complete(emitter);
        });
        emitter.onError(err -> {
            cleanup(sessionKey, disposable, logName, "emitter error");
            log.debug("{} SSE emitter error for {}: {}", logName, sessionKey, err.toString());
        });

        return emitter;
    }

    private void sendChunk(SseEmitter emitter, String sessionKey, String chunk) {
        try {
            emitter.send(chunk);
        } catch (IOException e) {
            log.debug("SSE send failed for {}: {}", sessionKey, e.getMessage());
            complete(emitter);
        }
    }

    private void completeWithMessage(SseEmitter emitter, String message) {
        try {
            emitter.send(message);
        } catch (IOException ignore) {
            // The connection is short-lived; completing below is enough.
        }
        complete(emitter);
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignore) {
            // Already completed or disconnected.
        }
    }

    private void cleanup(String sessionKey, Disposable disposable, String logName, String event) {
        if (!disposable.isDisposed()) {
            disposable.dispose();
        }
        activeEmitters.remove(sessionKey);
        log.info("{} SSE {} for {}", logName, event, sessionKey);
    }
}
