package eu.appbahn.shared.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("appbahn.eu")
@Version("v1")
@Kind("Resource")
public class ResourceCrd extends CustomResource<ResourceSpec, ResourceStatus> implements Namespaced {}
