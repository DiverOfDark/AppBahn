package eu.appbahn.shared.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import eu.appbahn.shared.crd.BuildConfig;
import eu.appbahn.shared.crd.BuildpackBuildConfig;
import eu.appbahn.shared.crd.DockerSource;
import eu.appbahn.shared.crd.DockerfileBuildConfig;
import eu.appbahn.shared.crd.GitSource;
import eu.appbahn.shared.crd.PeelboxBuildConfig;
import eu.appbahn.shared.crd.PromotionSource;
import eu.appbahn.shared.crd.RailpackBuildConfig;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import eu.appbahn.shared.crd.Source;
import eu.appbahn.tunnel.v1.LinkStatus;
import eu.appbahn.tunnel.v1.ResourceLink;
import eu.appbahn.tunnel.v1.ResourceStatusDetail;
import java.io.IOException;
import java.util.List;

/**
 * Converts between the CRD-side {@code eu.appbahn.shared.crd.*} POJOs and the wire-side
 * {@code eu.appbahn.tunnel.v1.*} proto messages the tunnel carries.
 *
 * <p>Most fields are non-polymorphic and round-trip cleanly via Jackson + protobuf's
 * {@link JsonFormat}. The two polymorphic types in the CRD ({@link Source} and
 * {@link BuildConfig}, both using Jackson's {@code @JsonTypeInfo(NAME)}) don't survive
 * the round-trip because proto's {@code oneof} JSON shape ({@code {"docker":{...}}}) and
 * Jackson's flattened-discriminator shape ({@code {"type":"docker", ...inline}}) disagree.
 * Those two have explicit per-variant builders below; everything else still uses the
 * round-trip helpers.
 */
public final class ProtoCrdMapper {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private static final JsonFormat.Parser PROTO_PARSER = JsonFormat.parser().ignoringUnknownFields();
    private static final JsonFormat.Printer PROTO_PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();

    private ProtoCrdMapper() {}

    // -------------------------------------------------------------------------
    // ResourceConfig
    // -------------------------------------------------------------------------

    public static eu.appbahn.tunnel.v1.ResourceConfig toProto(ResourceConfig crd) {
        if (crd == null) {
            return eu.appbahn.tunnel.v1.ResourceConfig.getDefaultInstance();
        }
        var builder = eu.appbahn.tunnel.v1.ResourceConfig.newBuilder();
        if (crd.getSource() != null) {
            builder.setSource(sourceToProto(crd.getSource()));
        }
        // Hosting / Networking / HealthCheck have no polymorphism — round-trip is fine.
        if (crd.getHosting() != null) {
            builder.setHosting(parseInto(crd.getHosting(), eu.appbahn.tunnel.v1.Hosting.newBuilder())
                    .build());
        }
        if (crd.getNetworking() != null) {
            builder.setNetworking(parseInto(crd.getNetworking(), eu.appbahn.tunnel.v1.Networking.newBuilder())
                    .build());
        }
        if (crd.getHealthCheck() != null) {
            builder.setHealthCheck(parseInto(crd.getHealthCheck(), eu.appbahn.tunnel.v1.HealthCheck.newBuilder())
                    .build());
        }
        if (crd.getEnv() != null) {
            builder.putAllEnv(crd.getEnv());
        }
        if (crd.getRunMode() != null) {
            builder.setRunMode(crd.getRunMode());
        }
        return builder.build();
    }

    public static ResourceConfig fromProto(eu.appbahn.tunnel.v1.ResourceConfig proto) {
        var crd = new ResourceConfig();
        if (proto.hasSource()) {
            crd.setSource(sourceFromProto(proto.getSource()));
        }
        if (proto.hasHosting()) {
            crd.setHosting(printAnd(proto.getHosting(), ResourceConfig.Hosting.class));
        }
        if (proto.hasNetworking()) {
            crd.setNetworking(printAnd(proto.getNetworking(), ResourceConfig.Networking.class));
        }
        if (proto.hasHealthCheck()) {
            crd.setHealthCheck(printAnd(proto.getHealthCheck(), ResourceConfig.HealthCheck.class));
        }
        if (proto.getEnvCount() > 0) {
            crd.setEnv(new java.util.LinkedHashMap<>(proto.getEnvMap()));
        }
        if (!proto.getRunMode().isEmpty()) {
            crd.setRunMode(proto.getRunMode());
        }
        return crd;
    }

    // -------------------------------------------------------------------------
    // Source (polymorphic — explicit per-variant)
    // -------------------------------------------------------------------------

    private static eu.appbahn.tunnel.v1.Source sourceToProto(Source src) {
        var builder = eu.appbahn.tunnel.v1.Source.newBuilder();
        if (src.getPollInterval() != null) {
            builder.setPollInterval(src.getPollInterval());
        }
        if (src.getWebhookEnabled() != null) {
            builder.setWebhookEnabled(src.getWebhookEnabled());
        }
        switch (src) {
            case DockerSource d -> {
                var v = eu.appbahn.tunnel.v1.DockerSource.newBuilder();
                if (d.getImage() != null) v.setImage(d.getImage());
                if (d.getTag() != null) v.setTag(d.getTag());
                if (d.getRegistryUrl() != null) v.setRegistryUrl(d.getRegistryUrl());
                if (d.getCredentialRef() != null) v.setCredentialRef(d.getCredentialRef());
                builder.setDocker(v);
            }
            case GitSource g -> {
                var v = eu.appbahn.tunnel.v1.GitSource.newBuilder();
                if (g.getUrl() != null) v.setUrl(g.getUrl());
                if (g.getBranch() != null) v.setBranch(g.getBranch());
                if (g.getPath() != null) v.setPath(g.getPath());
                if (g.getAuth() != null) {
                    v.setAuth(parseInto(g.getAuth(), eu.appbahn.tunnel.v1.SourceAuth.newBuilder())
                            .build());
                }
                if (g.getBuildConfig() != null) {
                    v.setBuildConfig(buildConfigToProto(g.getBuildConfig()));
                }
                builder.setGit(v);
            }
            case PromotionSource p -> {
                var v = eu.appbahn.tunnel.v1.PromotionSource.newBuilder();
                if (p.getSourceEnvironment() != null) v.setSourceEnvironment(p.getSourceEnvironment());
                if (p.getSourceResource() != null) v.setSourceResource(p.getSourceResource());
                if (p.getAutoPromote() != null) v.setAutoPromote(p.getAutoPromote());
                builder.setPromotion(v);
            }
            default -> throw new IllegalStateException("Unhandled Source variant: " + src.getClass());
        }
        return builder.build();
    }

    private static Source sourceFromProto(eu.appbahn.tunnel.v1.Source proto) {
        Source out =
                switch (proto.getVariantCase()) {
                    case DOCKER -> {
                        var d = new DockerSource();
                        d.setType("docker");
                        if (!proto.getDocker().getImage().isEmpty())
                            d.setImage(proto.getDocker().getImage());
                        if (!proto.getDocker().getTag().isEmpty())
                            d.setTag(proto.getDocker().getTag());
                        if (!proto.getDocker().getRegistryUrl().isEmpty())
                            d.setRegistryUrl(proto.getDocker().getRegistryUrl());
                        if (!proto.getDocker().getCredentialRef().isEmpty())
                            d.setCredentialRef(proto.getDocker().getCredentialRef());
                        yield d;
                    }
                    case GIT -> {
                        var g = new GitSource();
                        g.setType("git");
                        if (!proto.getGit().getUrl().isEmpty())
                            g.setUrl(proto.getGit().getUrl());
                        if (!proto.getGit().getBranch().isEmpty())
                            g.setBranch(proto.getGit().getBranch());
                        if (!proto.getGit().getPath().isEmpty())
                            g.setPath(proto.getGit().getPath());
                        if (proto.getGit().hasAuth()) {
                            g.setAuth(printAnd(proto.getGit().getAuth(), eu.appbahn.shared.crd.SourceAuth.class));
                        }
                        if (proto.getGit().hasBuildConfig()) {
                            g.setBuildConfig(buildConfigFromProto(proto.getGit().getBuildConfig()));
                        }
                        yield g;
                    }
                    case PROMOTION -> {
                        var p = new PromotionSource();
                        p.setType("promotion");
                        if (!proto.getPromotion().getSourceEnvironment().isEmpty())
                            p.setSourceEnvironment(proto.getPromotion().getSourceEnvironment());
                        if (!proto.getPromotion().getSourceResource().isEmpty())
                            p.setSourceResource(proto.getPromotion().getSourceResource());
                        if (proto.getPromotion().hasAutoPromote())
                            p.setAutoPromote(proto.getPromotion().getAutoPromote());
                        yield p;
                    }
                    case VARIANT_NOT_SET -> null;
                };
        if (out == null) {
            return null;
        }
        if (!proto.getPollInterval().isEmpty()) out.setPollInterval(proto.getPollInterval());
        if (proto.hasWebhookEnabled()) out.setWebhookEnabled(proto.getWebhookEnabled());
        return out;
    }

    // -------------------------------------------------------------------------
    // BuildConfig (polymorphic)
    // -------------------------------------------------------------------------

    private static eu.appbahn.tunnel.v1.BuildConfig buildConfigToProto(BuildConfig cfg) {
        var builder = eu.appbahn.tunnel.v1.BuildConfig.newBuilder();
        switch (cfg) {
            case BuildpackBuildConfig b -> {
                var v = eu.appbahn.tunnel.v1.BuildpackBuildConfig.newBuilder();
                if (b.getBuilder() != null) v.setBuilder(b.getBuilder());
                if (b.getBuildVars() != null) v.putAllBuildVars(b.getBuildVars());
                builder.setBuildpack(v);
            }
            case RailpackBuildConfig r -> {
                var v = eu.appbahn.tunnel.v1.RailpackBuildConfig.newBuilder();
                if (r.getProvider() != null) v.setProvider(r.getProvider());
                if (r.getBuildVars() != null) v.putAllBuildVars(r.getBuildVars());
                builder.setRailpack(v);
            }
            case DockerfileBuildConfig d -> {
                var v = eu.appbahn.tunnel.v1.DockerfileBuildConfig.newBuilder();
                if (d.getPath() != null) v.setPath(d.getPath());
                if (d.getTarget() != null) v.setTarget(d.getTarget());
                if (d.getBuildArgs() != null) v.putAllBuildArgs(d.getBuildArgs());
                builder.setDockerfile(v);
            }
            case PeelboxBuildConfig p -> {
                // PeelboxBuildConfig.universalBuild is List<Object> — intentionally lossy.
                builder.setPeelbox(eu.appbahn.tunnel.v1.PeelboxBuildConfig.getDefaultInstance());
            }
            default -> throw new IllegalStateException("Unhandled BuildConfig variant: " + cfg.getClass());
        }
        return builder.build();
    }

    private static BuildConfig buildConfigFromProto(eu.appbahn.tunnel.v1.BuildConfig proto) {
        return switch (proto.getVariantCase()) {
            case BUILDPACK -> {
                var b = new BuildpackBuildConfig();
                b.setType("buildpack");
                if (!proto.getBuildpack().getBuilder().isEmpty())
                    b.setBuilder(proto.getBuildpack().getBuilder());
                if (proto.getBuildpack().getBuildVarsCount() > 0)
                    b.setBuildVars(
                            new java.util.LinkedHashMap<>(proto.getBuildpack().getBuildVarsMap()));
                yield b;
            }
            case RAILPACK -> {
                var r = new RailpackBuildConfig();
                r.setType("railpack");
                if (!proto.getRailpack().getProvider().isEmpty())
                    r.setProvider(proto.getRailpack().getProvider());
                if (proto.getRailpack().getBuildVarsCount() > 0)
                    r.setBuildVars(
                            new java.util.LinkedHashMap<>(proto.getRailpack().getBuildVarsMap()));
                yield r;
            }
            case DOCKERFILE -> {
                var d = new DockerfileBuildConfig();
                d.setType("dockerfile");
                if (!proto.getDockerfile().getPath().isEmpty())
                    d.setPath(proto.getDockerfile().getPath());
                if (!proto.getDockerfile().getTarget().isEmpty())
                    d.setTarget(proto.getDockerfile().getTarget());
                if (proto.getDockerfile().getBuildArgsCount() > 0)
                    d.setBuildArgs(
                            new java.util.LinkedHashMap<>(proto.getDockerfile().getBuildArgsMap()));
                yield d;
            }
            case PEELBOX -> {
                var p = new PeelboxBuildConfig();
                p.setType("peelbox");
                yield p;
            }
            case VARIANT_NOT_SET -> null;
        };
    }

    // -------------------------------------------------------------------------
    // ResourceLink
    // -------------------------------------------------------------------------

    public static ResourceLink toProto(ResourceSpec.ResourceLink link) {
        if (link == null) {
            return ResourceLink.getDefaultInstance();
        }
        return parseInto(link, ResourceLink.newBuilder()).build();
    }

    public static List<ResourceLink> toProto(List<ResourceSpec.ResourceLink> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream().map(ProtoCrdMapper::toProto).toList();
    }

    public static List<ResourceSpec.ResourceLink> linksFromProto(List<ResourceLink> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(l -> printAnd(l, ResourceSpec.ResourceLink.class))
                .toList();
    }

    // -------------------------------------------------------------------------
    // ResourceStatus / status fragments
    // -------------------------------------------------------------------------

    public static ResourceStatusDetail toProto(ResourceStatus status) {
        if (status == null) {
            return ResourceStatusDetail.getDefaultInstance();
        }
        // Status enums serialise to their names — exactly what the proto string fields want.
        return parseInto(status, ResourceStatusDetail.newBuilder()).build();
    }

    public static ResourceStatus fromProto(ResourceStatusDetail proto) {
        return printAnd(proto, ResourceStatus.class);
    }

    public static LinkStatus toProto(ResourceStatus.LinkStatus crd) {
        if (crd == null) {
            return LinkStatus.getDefaultInstance();
        }
        return parseInto(crd, LinkStatus.newBuilder()).build();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static <B extends Message.Builder> B parseInto(Object pojo, B builder) {
        try {
            PROTO_PARSER.merge(MAPPER.writeValueAsString(pojo), builder);
            return builder;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to convert " + pojo.getClass().getSimpleName() + " to proto", e);
        }
    }

    private static <T> T printAnd(Message proto, Class<T> target) {
        try {
            return MAPPER.readValue(PROTO_PRINTER.print(proto), target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert proto to " + target.getSimpleName(), e);
        }
    }
}
