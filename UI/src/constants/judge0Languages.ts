export type SupportedJudge0Language = {
  value: string;
  label: string;
  judge0LanguageId: number;
};

export const SUPPORTED_JUDGE0_LANGUAGES = [
  { value: "c", label: "C", judge0LanguageId: 50 },
  { value: "cpp", label: "C++17", judge0LanguageId: 54 },
  { value: "java", label: "Java", judge0LanguageId: 62 },
  { value: "python", label: "Python", judge0LanguageId: 71 },
  { value: "javascript", label: "JavaScript", judge0LanguageId: 63 },
  { value: "go", label: "Go", judge0LanguageId: 60 },
] as const satisfies readonly SupportedJudge0Language[];

export const DEFAULT_VALIDATOR_LANGUAGE_ID = 54;

export function getJudge0LanguageOptionById(languageId?: number | null) {
  return SUPPORTED_JUDGE0_LANGUAGES.find((language) => language.judge0LanguageId === languageId);
}
