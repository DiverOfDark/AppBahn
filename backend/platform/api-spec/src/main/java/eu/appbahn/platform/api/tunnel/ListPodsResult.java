package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Per-pod snapshot returned by the operator in response to {@link ListPods}. Usage fields
 * are populated from metrics-server when present (best-effort — if the metrics API isn't
 * installed they stay null, and the UI shows limits-only).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListPodsResult extends CommandResponsePayload {

    private List<ListPodsResultEntry> pods = new ArrayList<>();

    public ListPodsResult() {
        setType("list-pods-result");
    }
}
