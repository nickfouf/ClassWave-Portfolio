const mediasoupConfig = require('../config/mediasoupConfig');

class RoomManager {
    constructor() {
        this.routers = new Map();
        this.activeSpeakerObservers = new Map();
        this.activeSpeakerObserverPromises = new Map();
    }

    async getOrCreateRoom(roomId, worker) {
        if (this.routers.has(roomId)) {
            console.log(`[RoomManager] Το δωμάτιο ${roomId} υπάρχει ήδη.`);
            return this.routers.get(roomId);
        }

        console.log(`[RoomManager] Δημιουργία νέου Router για το δωμάτιο ${roomId}...`);

        const router = await worker.createRouter({
            mediaCodecs: mediasoupConfig.router.mediaCodecs
        });

        this.routers.set(roomId, router);

        return router;
    }

    async getOrCreateActiveSpeakerObserver(roomId, onDominantSpeaker) {
        const existingObserver = this.activeSpeakerObservers.get(roomId);
        if (existingObserver) return existingObserver;

        const pendingObserver = this.activeSpeakerObserverPromises.get(roomId);
        if (pendingObserver) return pendingObserver;

        const router = this.routers.get(roomId);
        if (!router) {
            throw new Error(`Το δωμάτιο ${roomId} δεν βρέθηκε.`);
        }

        const observerPromise = (async () => {
            const observer = await router.createActiveSpeakerObserver({ interval: 500 });

            observer.on('dominantspeaker', ({ producer }) => {
                onDominantSpeaker(producer);
            });
            observer.on('routerclose', () => {
                if (this.activeSpeakerObservers.get(roomId) === observer) {
                    this.activeSpeakerObservers.delete(roomId);
                }
            });

            this.activeSpeakerObservers.set(roomId, observer);
            console.log(`[Mediasoup] Ενεργοποιήθηκε active-speaker detection στο δωμάτιο ${roomId}.`);
            return observer;
        })();

        this.activeSpeakerObserverPromises.set(roomId, observerPromise);

        try {
            return await observerPromise;
        } finally {
            if (this.activeSpeakerObserverPromises.get(roomId) === observerPromise) {
                this.activeSpeakerObserverPromises.delete(roomId);
            }
        }
    }

    async registerAudioProducer(roomId, producer) {
        const observer = this.activeSpeakerObservers.get(roomId);
        if (!observer) {
            throw new Error(`Δεν υπάρχει active-speaker observer για το δωμάτιο ${roomId}.`);
        }
        await observer.addProducer({ producerId: producer.id });
    }

    getRouterRtpCapabilities(roomId) {
        const router = this.routers.get(roomId);
        if (!router) {
            throw new Error(`Το δωμάτιο ${roomId} δεν βρέθηκε.`);
        }
        return router.rtpCapabilities;
    }

    removeRoom(roomId) {
        const router = this.routers.get(roomId);
        if (router) {
            const observer = this.activeSpeakerObservers.get(roomId);
            if (observer && !observer.closed) observer.close();
            this.activeSpeakerObservers.delete(roomId);
            this.activeSpeakerObserverPromises.delete(roomId);
            router.close();
            this.routers.delete(roomId);
            console.log(`[RoomManager] Το δωμάτιο ${roomId} έκλεισε.`);
        }
    }

    async createTransport(roomId) {
        const router = this.routers.get(roomId);
        if (!router) {
            throw new Error(`Το δωμάτιο ${roomId} δεν βρέθηκε.`);
        }

        const { webRtcTransport } = require('../config/mediasoupConfig');

        const transport = await router.createWebRtcTransport({
            listenIps: webRtcTransport.listenIps,
            enableUdp: true,
            enableTcp: true,
            preferUdp: true,
            initialAvailableOutgoingBitrate: webRtcTransport.initialAvailableOutgoingBitrate,
        });

        console.log(`[Mediasoup] Δημιουργήθηκε Transport: ${transport.id} στο δωμάτιο ${roomId}`);

        return {
            transport,
            params: {
                id: transport.id,
                iceParameters: transport.iceParameters,
                iceCandidates: transport.iceCandidates,
                dtlsParameters: transport.dtlsParameters,
            }
        };
    }
}

module.exports = new RoomManager();
