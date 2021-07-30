package com.ldeng7.learningwebrtc;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class WebRTCWsMessage {
    public enum EPhase {
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

    public static class Response<T> {
        public EPhase phase;
        public boolean success;
        public String message;
        public T data;
    }

    public static class DialRequestData {
        @SerializedName("uid")
        public String localUid;
        @SerializedName("ruid")
        public String remoteUid;
    }

    public static class SdpData {
        public String type;
        public String sdp;
    }

    public static class CandidateData {
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

    public static String encode(EPhase phase, Object data) {
        Request req = new Request();
        req.phase = phase.ordinal();
        req.data = g.toJson(data);
        return g.toJson(req, Request.class);
    }

    public static Response decode(String s) {
        ResponseInternal ri = g.fromJson(s, ResponseInternal.class);
        EPhase phase = EPhase.values()[ri.phase];
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
