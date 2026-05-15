import { useState, useEffect, useRef, useCallback } from "react";

/**
 * Generates a unique localStorage key for code draft persistence.
 * Key pattern: draft_{userId}_{contestId}_{problemId}_{language}
 */
export const CODE_DRAFT_FLUSH_EVENT = "aurac:flush-code-draft";

export const getDraftKey = (
    userId: string,
    contestId: string,
    problemId: string,
    language: string
): string => `draft_${userId}_${contestId}_${problemId}_${language}`;

/**
 * Clears a specific draft from localStorage.
 * Can be called from anywhere (not just inside the hook).
 */
export function clearDraftFromStorage(
    userId: string,
    contestId: string,
    problemId: string,
    language: string
): void {
    const key = getDraftKey(userId, contestId, problemId, language);
    try {
        localStorage.removeItem(key);
    } catch {
        console.warn("Failed to clear draft from localStorage");
    }
}

/**
 * Hook for LeetCode-style code draft persistence using browser localStorage.
 *
 * Save triggers:
 * 1. Debounced auto-save (320ms) while typing
 * 2. Immediate save when user switches problem
 * 3. Immediate save when user switches language
 * 4. Immediate save on tab close / page refresh (beforeunload)
 * 5. Immediate save on page visibility change (Alt+Tab, minimize)
 */
export function useCodeDraft(
    userId: string,
    contestId: string,
    problemId: string,
    language: string,
    initialCode = ""
) {
    const [code, setCodeState] = useState<string>("");

    // Refs to always have the latest values without stale closures
    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isInitialMountRef = useRef(true);
    const onSavedCallbackRef = useRef<(() => void) | null>(null);

    // Keep latest code/key in refs so event listeners never go stale
    const codeRef = useRef<string>("");
    const currentKeyRef = useRef<string>("");
    const initialCodeRef = useRef<string>(initialCode);
    initialCodeRef.current = initialCode;

    // Keep previous problemId + language to save BEFORE switching
    const prevProblemIdRef = useRef<string>(problemId);
    const prevLanguageRef = useRef<string>(language);
    const prevContestIdRef = useRef<string>(contestId);

    // Always sync codeRef to latest code value
    useEffect(() => {
        codeRef.current = code;
    }, [code]);

    // Always sync currentKeyRef to latest key
    useEffect(() => {
        currentKeyRef.current = getDraftKey(userId, contestId, problemId, language);
    }, [userId, contestId, problemId, language]);

    /**
     * Core save function — writes current code to localStorage.
     * Uses refs so it never has stale values.
     */
    const saveNow = useCallback((key: string, value: string) => {
        if (!key) return;

        try {
            localStorage.setItem(key, value);
        } catch {
            console.warn("Failed to save draft to localStorage");
        }
    }, []);

    /**
     * Safely loads a draft from localStorage.
     * Returns null only when no saved draft exists or storage is unavailable.
     */
    const loadDraft = useCallback((key: string): string | null => {
        try {
            return localStorage.getItem(key);
        } catch {
            return null;
        }
    }, []);

    /**
     * Clears the draft for the current problem + language.
     */
    const clearDraft = useCallback(() => {
        const key = getDraftKey(userId, contestId, problemId, language);
        try {
            localStorage.removeItem(key);
        } catch {
            console.warn("Failed to clear draft from localStorage");
        }
    }, [userId, contestId, problemId, language]);

    /**
     * Sets a callback to be invoked after each save.
     * Used to trigger the "Draft saved" indicator.
     */
    const setOnSavedCallback = useCallback((callback: (() => void) | null) => {
        onSavedCallbackRef.current = callback;
    }, []);

    /* ============================================================
       TRIGGER 1 — Load draft when problem / language / contest changes
       ALSO: Save the PREVIOUS draft immediately before switching
    ============================================================ */
    useEffect(() => {
        const prevKey = getDraftKey(
            userId,
            prevContestIdRef.current,
            prevProblemIdRef.current,
            prevLanguageRef.current
        );

        // Save the old draft immediately before we switch
        // Skip on very first mount (nothing to save yet)
        if (!isInitialMountRef.current) {
            saveNow(prevKey, codeRef.current);
        }

        // Update previous refs to current values
        prevProblemIdRef.current = problemId;
        prevLanguageRef.current = language;
        prevContestIdRef.current = contestId;

        // Load the draft for the new problem/language.
        // If the key exists, even as an empty string, keep it instead of applying starter code.
        const newKey = getDraftKey(userId, contestId, problemId, language);
        const loaded = loadDraft(newKey);
        setCodeState(loaded ?? initialCodeRef.current);

        isInitialMountRef.current = false;
    }, [userId, contestId, problemId, language, loadDraft, saveNow]);

    /* ============================================================
       TRIGGER 2 — Debounced auto-save while typing (320ms)
    ============================================================ */
    useEffect(() => {
        if (isInitialMountRef.current) return;

        if (debounceTimerRef.current) {
            clearTimeout(debounceTimerRef.current);
        }

        const key = getDraftKey(userId, contestId, problemId, language);
        const codeToSave = code;

        debounceTimerRef.current = setTimeout(() => {
            saveNow(key, codeToSave);
            onSavedCallbackRef.current?.();
        }, 1500);

        return () => {
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current);
            }
        };
    }, [code, userId, contestId, problemId, language, saveNow]);

    /* ============================================================
       TRIGGER 3 — Save on tab close / page refresh (beforeunload)
       This fires even if debounce hasn't completed yet
    ============================================================ */
    useEffect(() => {
        const handleBeforeUnload = () => {
            saveNow(currentKeyRef.current, codeRef.current);
        };
        const handleFlushDraft = () => {
            saveNow(currentKeyRef.current, codeRef.current);
        };

        window.addEventListener("beforeunload", handleBeforeUnload);
        window.addEventListener(CODE_DRAFT_FLUSH_EVENT, handleFlushDraft);

        return () => {
            window.removeEventListener("beforeunload", handleBeforeUnload);
            window.removeEventListener(CODE_DRAFT_FLUSH_EVENT, handleFlushDraft);
        };
    }, [saveNow]);

    /* ============================================================
       TRIGGER 4 — Save when tab loses visibility
       Covers: Alt+Tab, minimizing, switching browser tabs
    ============================================================ */
    useEffect(() => {
        const handleVisibilityChange = () => {
            if (document.visibilityState === "hidden") {
                saveNow(currentKeyRef.current, codeRef.current);
            }
        };

        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
        };
    }, [saveNow]);

    /* ============================================================
       TRIGGER 5 - Save on component unmount
       Protects pending debounced edits if a layout or tab accidentally remounts the editor.
    ============================================================ */
    useEffect(() => {
        return () => {
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current);
            }

            saveNow(currentKeyRef.current, codeRef.current);
        };
    }, [saveNow]);

    /**
     * Updates the code state.
     * Actual localStorage save is debounced (Trigger 2).
     */
    const setCode = useCallback((newCode: string) => {
        codeRef.current = newCode;
        setCodeState(newCode);
    }, []);

    return {
        code,
        setCode,
        clearDraft,
        setOnSavedCallback,
    };
}
