const os = require('os');

function getLocalIp() {
    const ifaces = os.networkInterfaces();
    for (const dev in ifaces) {
        for (const details of ifaces[dev]) {
            if (details.family === 'IPv4' && !details.internal) {
                return details.address;
            }
        }
    }
    return '127.0.0.1';
}

const announcedIp = process.env.MEDIASOUP_ANNOUNCED_IP || getLocalIp();

module.exports = {
    worker: {
        rtcMinPort: 10000,
        rtcMaxPort: 10100,
        logLevel: 'warn',
        logTags: [
            'info',
            'ice',
            'dtls',
            'rtp',
            'srtp',
            'rtcp'
        ]
    },

    router: {
        mediaCodecs: [
            {
                kind: 'audio',
                mimeType: 'audio/opus',
                clockRate: 48000,
                channels: 2
            },
            {
                kind: 'video',
                mimeType: 'video/VP8',
                clockRate: 90000,
                parameters: {
                    'x-google-start-bitrate': 1000
                }
            },
            {
                kind: 'video',
                mimeType: 'video/VP9',
                clockRate: 90000,
                parameters: {
                    'profile-id': 2,
                    'x-google-start-bitrate': 1000
                }
            }
        ]
    },

    webRtcTransport: {
        listenIps: [
            {
                ip: '0.0.0.0',
                announcedIp: announcedIp
            }
        ],
        initialAvailableOutgoingBitrate: 1000000,
        minimumAvailableOutgoingBitrate: 600000,
        maxSctpMessageSize: 262144
    }
};