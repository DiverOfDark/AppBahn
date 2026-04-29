package eu.appbahn.operator.reconciler.imagesource;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the HEAD commit SHA of a remote git repo via JGit's
 * {@link LsRemoteCommand}, which speaks smart HTTP without needing a local working repo.
 * Auth is loaded from the Kubernetes Secret named in
 * {@link eu.appbahn.shared.crd.imagesource.ImageSourceGitSpec#getCredentialsSecretRef()};
 * the Secret's {@code type} field selects the credential provider — {@code kubernetes.io/basic-auth}
 * uses {@link UsernamePasswordCredentialsProvider}; {@code kubernetes.io/ssh-auth} is recognized
 * but full SSH-key support lands when the operator gains a session-factory bridge (out of scope
 * for this PR).
 */
@Component
public class GitClient {

    /** Standard K8s secret type for username + password / token credentials. */
    static final String SECRET_TYPE_BASIC_AUTH = "kubernetes.io/basic-auth";

    /** Standard K8s secret type for an SSH private key. */
    static final String SECRET_TYPE_SSH_AUTH = "kubernetes.io/ssh-auth";

    private final KubernetesClient kubernetesClient;

    public GitClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Look up {@code refs/heads/{branch}} on {@code repo} and return its commit SHA. Throws if
     * the remote is unreachable, the branch is missing, or auth fails — the reconciler turns
     * the exception into a {@code Ready=False reason=PollFailed} condition with the message.
     */
    public String resolveHead(String repo, String branch, String namespace, String secretName) throws Exception {
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("git.repo is required");
        }
        String effectiveBranch = (branch == null || branch.isBlank()) ? Constants.MASTER : branch;
        String refName = Constants.R_HEADS + effectiveBranch;

        // ls-remote needs a Repository to drive the protocol exchange (smart HTTP and local
        // file:// both go through Transport.open(Repository, ...)). The empty-bare-repo path
        // returned by Git.lsRemoteRepository() is a NPE trap on this JGit version, so we
        // create a throwaway bare repo on disk for the duration of the call. Cost: ~1ms per
        // poll, negligible relative to the HTTP round-trip.
        File scratchDir = Files.createTempDirectory("appbahn-lsremote-").toFile();
        try (Repository scratch =
                new RepositoryBuilder().setBare().setGitDir(scratchDir).build()) {
            scratch.create(true);
            LsRemoteCommand cmd = Git.wrap(scratch).lsRemote().setRemote(repo).setHeads(true);
            CredentialsProvider creds = loadCredentials(namespace, secretName);
            if (creds != null) {
                cmd.setCredentialsProvider(creds);
            }
            Ref ref = cmd.callAsMap().get(refName);
            if (ref == null || ref.getObjectId() == null) {
                throw new IllegalStateException("branch '" + effectiveBranch + "' not found on " + repo);
            }
            return ref.getObjectId().getName();
        } finally {
            FileUtils.delete(scratchDir, FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
        }
    }

    private CredentialsProvider loadCredentials(String namespace, String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return null;
        }
        Secret secret = kubernetesClient
                .secrets()
                .inNamespace(namespace)
                .withName(secretName)
                .get();
        if (secret == null || secret.getData() == null) {
            throw new IllegalStateException("credentials secret " + namespace + "/" + secretName + " not found");
        }
        String type = secret.getType();
        if (type == null || SECRET_TYPE_BASIC_AUTH.equals(type)) {
            String username = decode(secret.getData().get("username"));
            String password = decode(secret.getData().get("password"));
            return new UsernamePasswordCredentialsProvider(
                    username == null ? "" : username, password == null ? "" : password);
        }
        if (SECRET_TYPE_SSH_AUTH.equals(type)) {
            // SSH transport needs JGit's session-factory bridge with a TransportConfigCallback;
            // wiring that up is deferred — surface a clear error so the user gets a ConfigInvalid
            // condition instead of an opaque NPE deep in JGit.
            throw new IllegalStateException("kubernetes.io/ssh-auth secrets are not yet supported");
        }
        throw new IllegalStateException(
                "unsupported credentials secret type '" + type + "' on " + namespace + "/" + secretName);
    }

    private static String decode(String base64) {
        if (base64 == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(base64));
    }
}
