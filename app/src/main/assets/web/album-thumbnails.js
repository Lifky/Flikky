/* Session-only thumbnail bytes. Full-size photos still use authenticated streaming URLs. */
(function () {
    'use strict';

    window.createAlbumThumbnailCache = function (options) {
        const settings = options || {};
        const maxBytes = settings.maxBytes ?? 32 * 1024 * 1024;
        const maxEntries = settings.maxEntries ?? 512;
        const cached = new Map();
        const pending = new Map();
        const queue = [];
        let bytes = 0;
        let running = 0;
        let generation = 0;

        function remember(id, blob) {
            if (blob.size > maxBytes || maxEntries <= 0) return;
            cached.set(id, blob);
            bytes += blob.size;
            while (bytes > maxBytes || cached.size > maxEntries) {
                const oldest = cached.keys().next().value;
                bytes -= cached.get(oldest).size;
                cached.delete(oldest);
            }
        }

        async function download(request) {
            running += 1;
            try {
                const response = await fetch('/api/album/thumb?id=' + encodeURIComponent(request.id), {
                    credentials: 'same-origin', cache: 'no-store', signal: request.controller.signal,
                });
                if (request.generation !== generation) { request.resolve(null); return; }
                if (!response.ok) throw new Error('thumbnail unavailable: ' + response.status);
                const blob = await response.blob();
                if (request.generation !== generation) { request.resolve(null); return; }
                if (!blob.size) throw new Error('empty thumbnail');
                remember(request.id, blob);
                request.resolve(blob);
            } catch (error) {
                if (request.generation !== generation) request.resolve(null);
                else request.reject(error);
            } finally {
                running -= 1;
                if (pending.get(request.id) === request) pending.delete(request.id);
                pump();
            }
        }

        function pump() {
            while (running < 6 && queue.length) {
                const request = queue.shift();
                if (!request.consumers.some((needed) => needed())) {
                    pending.delete(request.id);
                    request.resolve(null);
                    continue;
                }
                download(request);
            }
        }

        function get(id, isNeeded = () => true) {
            if (cached.has(id)) {
                const blob = cached.get(id);
                cached.delete(id);
                cached.set(id, blob);
                return Promise.resolve(blob);
            }
            if (pending.has(id)) {
                const request = pending.get(id);
                request.consumers.push(isNeeded);
                return request.promise;
            }
            const request = { id, generation, controller: new AbortController(), consumers: [isNeeded] };
            request.promise = new Promise((resolve, reject) => {
                request.resolve = resolve;
                request.reject = reject;
            });
            pending.set(id, request);
            queue.push(request);
            pump();
            return request.promise;
        }

        function clear() {
            generation += 1;
            cached.clear();
            bytes = 0;
            pending.forEach((request) => { request.controller.abort(); request.resolve(null); });
            pending.clear();
            queue.length = 0;
        }

        return { get, clear };
    };
})();
