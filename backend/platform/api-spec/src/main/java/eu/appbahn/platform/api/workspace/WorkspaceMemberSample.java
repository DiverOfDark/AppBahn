package eu.appbahn.platform.api.workspace;

import eu.appbahn.platform.api.WorkspaceMember;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Up to {@code limit} active members of a workspace, plus the absolute total. The console
 * uses this to render the stacked avatars + "+N more" badge on workspace cards without
 * issuing one {@code GET /workspaces/{slug}/members} per card.
 */
@Data
public class WorkspaceMemberSample {

    @Nullable
    private String slug;

    @Valid
    private List<WorkspaceMember> members = new ArrayList<>();

    private int totalCount;
}
