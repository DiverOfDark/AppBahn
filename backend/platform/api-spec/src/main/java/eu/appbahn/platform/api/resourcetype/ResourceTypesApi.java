package eu.appbahn.platform.api.resourcetype;

import eu.appbahn.platform.api.ResourceTypeInfo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "ResourceTypes")
public interface ResourceTypesApi {
    /**
     * GET /resource-types : ListResourceTypes
     *
     * @param cluster  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resource-types",
            produces = {"application/json"})
    ResponseEntity<List<ResourceTypeInfo>> listResourceTypes(
            @Valid @RequestParam(value = "cluster", required = false) @Nullable String cluster);
}
