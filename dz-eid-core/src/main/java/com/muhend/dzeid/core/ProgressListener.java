package com.muhend.dzeid.core;

/** Suivi de progression d'une lecture (affichage d'une barre ou d'un message à l'utilisateur). */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (step, percent, message) -> { };

    /**
     * @param step    étape courante
     * @param percent avancement estimé 0-100
     * @param message message en français, affichable tel quel
     */
    void onProgress(Step step, int percent, String message);

    enum Step {
        CONNECTING, AUTHENTICATING, READING, VERIFYING, DONE
    }
}
