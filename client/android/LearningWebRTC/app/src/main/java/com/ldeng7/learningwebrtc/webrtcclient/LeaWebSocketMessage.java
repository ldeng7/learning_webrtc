package com.ldeng7.learningwebrtc.webrtcclient;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

class LeaWebSocketMessage {
    enum EPhase {
        INIT,
        DIAL,
        OFFER,
        ANS,
        CAND,
        STOP,
    }

    private static class Request {
        public int phase;
        public String data;
    }

    private static class ResponseInternal {
        public int phase;
        public boolean success;
        public String message;
        public String data;
    }

    static class Response<T> {
        public EPhase phase;
        public boolean success;
        public String message;
        public T data;
    }

    static class DialRequestData {
        @SerializedName("uid")
        public String localUid;
        @SerializedName("ruid")
        public String remoteUid;
    }

    static class SdpData {
        public String type;
        public String sdp;
    }

    static class CandidateData {
        public String candidate;
        public String sdpMid;
        public int sdpMLineIndex;
    }

    private static final Gson g = new Gson();
    private static final Map<EPhase, Class> phaseToDataClass = new HashMap<>();
    static {
        phaseToDataClass.put(EPhase.DIAL, Boolean.class);
        phaseToDataClass.put(EPhase.OFFER, SdpData.class);
        phaseToDataClass.put(EPhase.ANS, SdpData.class);
        phaseToDataClass.put(EPhase.CAND, CandidateData.class);
    }

    static String encode(final EPhase phase, final Object data) {
        Request req = new Request();
        req.phase = phase.ordinal();
        req.data = g.toJson(data);
        return g.toJson(req, Request.class);
    }

    static Response decode(String s) {
        final ResponseInternal ri = g.fromJson(s, ResponseInternal.class);
        final EPhase phase = EPhase.values()[ri.phase];
        Response r = new Response();
        r.phase = phase;
        r.success = ri.success;
        r.message = ri.message;
        if (phaseToDataClass.containsKey(phase)) {
            r.data = g.fromJson(ri.data, phaseToDataClass.get(phase));
        }
        return r;
    }
}
