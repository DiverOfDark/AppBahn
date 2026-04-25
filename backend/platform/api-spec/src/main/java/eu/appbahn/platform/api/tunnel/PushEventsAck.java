package eu.appbahn.platform.api.tunnel;

import lombok.Data;

@Data
public class PushEventsAck {

    /** Echoed count so the operator can sanity-check the call landed on a platform replica. */
    private int acceptedCount;
}
