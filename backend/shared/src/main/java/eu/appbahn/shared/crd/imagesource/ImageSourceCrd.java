package eu.appbahn.shared.crd.imagesource;

import eu.appbahn.shared.crd.CrdSchemaVersion;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("appbahn.eu")
@Version("v1")
@Kind("ImageSource")
@CrdSchemaVersion(kind = "ImageSource", version = 1)
public class ImageSourceCrd extends CustomResource<ImageSourceSpec, ImageSourceStatus> implements Namespaced {}
