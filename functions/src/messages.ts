import { DateTime } from "luxon";
import { validZone } from "./schedule";

const BN_DIGITS = "০১২৩৪৫৬৭৮৯";

export function bnDigits(text: string): string {
  return text.replace(/\d/g, (d) => BN_DIGITS[Number(d)]);
}

export function parentLabel(relation: string | undefined, name: string, language: string): string {
  if (relation === "mother") return language === "en" ? "Amma" : "আম্মা";
  if (relation === "father") return language === "en" ? "Abba" : "আব্বা";
  return name;
}

export interface MissedAlert {
  title: string;
  body: string;
}

/** "আম্মা ৮:০০টার প্রেশারের ওষুধ এখনো খাননি", in the parent's local time. */
export function missedDoseAlert(
  parent: { name: string; relation?: string; timezone?: string },
  medicineName: string,
  scheduledAt: number,
  language: string,
): MissedAlert {
  const local = DateTime.fromMillis(scheduledAt, { zone: validZone(parent.timezone) });
  const who = parentLabel(parent.relation, parent.name, language);
  if (language === "en") {
    return {
      title: "Missed dose",
      body: `${who} hasn't taken the ${local.toFormat("h:mm a")} ${medicineName} yet`,
    };
  }
  return {
    title: "ওষুধ মিস হয়েছে",
    body: `${who} ${bnDigits(local.toFormat("h:mm"))}টার ${medicineName} এখনো খাননি`,
  };
}
