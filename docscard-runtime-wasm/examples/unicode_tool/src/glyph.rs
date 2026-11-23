use serde_json::{json, Value};
use unicode_general_category::{get_general_category, GeneralCategory::*};

pub fn describe(c: char) -> Value {
    let (preview, kind) = match get_general_category(c) {
        NonspacingMark | SpacingMark | EnclosingMark => (format!("◌{c}"), "组合标记"),
        SpaceSeparator | LineSeparator | ParagraphSeparator => (String::new(), "空白字符"),
        Control | Format => (String::new(), "不可见字符"),
        Unassigned => (String::new(), "未分配码点"),
        PrivateUse => (c.to_string(), "私用区"),
        _ => (c.to_string(), "字符"),
    };
    json!({"code_point": format!("U+{:04X}", c as u32), "preview": preview,
           "kind": kind, "name": unicode_names2::name(c).map(|n| n.to_string())})
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn separates_marks_invisible_unassigned_and_supplementary() {
        assert_eq!(describe('\u{301}')["preview"], "◌\u{301}");
        assert_eq!(describe(' ')["kind"], "空白字符");
        for c in ['\n', '\u{200d}'] {
            assert_eq!(describe(c)["preview"], "");
        }
        assert_eq!(describe('\u{378}')["kind"], "未分配码点");
        assert_eq!(describe('😀')["name"], "GRINNING FACE");
        assert_eq!(describe('😀')["code_point"], "U+1F600");
    }
}
