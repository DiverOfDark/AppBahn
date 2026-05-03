package eu.appbahn.platform.resource.service;

/**
 * Dispatches a webhook-triggered nudge to the operator that owns the cluster hosting an
 * ImageSource. The implementation enqueues a tunnel command; the operator side patches
 * {@code status.lastWebhookAt} and triggers a fresh reconcile, which re-pulls HEAD itself.
 */
public interface ImageSourceNudger {

    /** Send a nudge for the ImageSource identified by its slug. */
    void nudge(String slug);
}
