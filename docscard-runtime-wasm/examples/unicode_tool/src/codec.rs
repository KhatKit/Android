use serde_json::{json, Value};
use std::fmt::Write;

const MAX_SCALARS: usize = 512;
const TOO_LONG: &str = "最多支持 512 个码点，编码输入最长为 8192 个 UTF-16 单元";
const BAD_ENCODING: &str = "编码格式无效：请输入十六进制码点或完整的 \\uXXXX 转义";
const BAD_SCALAR: &str = "不是有效的 Unicode 标量，或 UTF-16 代理项不成对";

fn separator(c: char) -> bool {
    c.is_whitespace() || matches!(c, ',' | '，')
}

fn decode(input: &str) -> Result<String, &'static str> {
    let input = input.trim();
    if input.is_empty() {
        return Ok(String::new());
    }
    if input.starts_with("\\u") {
        let mut units = Vec::new();
        let mut rest = input;
        while !rest.is_empty() {
            let digits = rest
                .strip_prefix("\\u")
                .and_then(|s| s.get(..4))
                .ok_or(BAD_ENCODING)?;
            if !digits.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err(BAD_ENCODING);
            }
            units.push(u16::from_str_radix(digits, 16).map_err(|_| BAD_ENCODING)?);
            if units.len() > MAX_SCALARS * 2 {
                return Err(TOO_LONG);
            }
            rest = rest[6..].trim_start_matches(separator);
        }
        return String::from_utf16(&units).map_err(|_| BAD_SCALAR);
    }
    let mut text = String::new();
    for (i, token) in input.split(separator).filter(|s| !s.is_empty()).enumerate() {
        if i >= MAX_SCALARS {
            return Err(TOO_LONG);
        }
        let hex = ["U+", "u+", "0x", "0X"]
            .iter()
            .find_map(|p| token.strip_prefix(p))
            .unwrap_or(token);
        if !(1..=6).contains(&hex.len()) || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(BAD_ENCODING);
        }
        let n = u32::from_str_radix(hex, 16).map_err(|_| BAD_ENCODING)?;
        text.push(char::from_u32(n).ok_or(BAD_SCALAR)?);
    }
    if text.is_empty() {
        Err(BAD_ENCODING)
    } else {
        Ok(text)
    }
}

pub fn convert(mode: &str, input: &str) -> Result<Value, &'static str> {
    if input.encode_utf16().take(8193).count() > 8192 {
        return Err(TOO_LONG);
    }
    let text = match mode {
        "encode" => input.to_owned(),
        "decode" => decode(input)?,
        _ => return Err("未知的转换方式"),
    };
    let count = text.chars().count();
    if count > MAX_SCALARS {
        return Err(TOO_LONG);
    }
    let mut points = String::new();
    let mut escapes = String::new();
    for c in text.chars() {
        if !points.is_empty() {
            points.push(' ');
        }
        write!(points, "U+{:04X}", c as u32).unwrap();
    }
    for unit in text.encode_utf16() {
        write!(escapes, "\\u{unit:04X}").unwrap();
    }
    Ok(json!({"text": text, "code_points": points, "utf16": escapes, "count": count}))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_without_normalizing() {
        for text in ["", "A中😀", " e\u{301}\n", "👩‍💻", "\0\u{10ffff}"] {
            let encoded = convert("encode", text).unwrap();
            for key in ["code_points", "utf16"] {
                assert_eq!(
                    convert("decode", encoded[key].as_str().unwrap()).unwrap()["text"],
                    text
                );
            }
        }
    }
    #[test]
    fn accepts_mixed_separators_and_hex_case() {
        assert_eq!(
            convert("decode", "0x41，u+4e2d\n1F600").unwrap()["text"],
            "A中😀"
        );
        assert_eq!(convert("decode", r"\uD83D, \uDE00").unwrap()["text"], "😀");
    }
    #[test]
    fn rejects_partial_invalid_or_unpaired_input() {
        for bad in [
            "+FFF",
            "110000",
            "D800",
            "0x",
            "U+0041x",
            ",",
            r"\u😀",
            r"\u123",
            r"\uD800\u0041",
            r"\uDFFF",
            r"\u0041oops",
        ] {
            assert!(convert("decode", bad).is_err(), "{bad}");
        }
    }
    #[test]
    fn checks_scalar_limit_after_combining_surrogates() {
        assert_eq!(
            convert("decode", &r"\uD83D\uDE00".repeat(512)).unwrap()["count"],
            512
        );
        assert_eq!(convert("decode", &r"\u0041".repeat(513)), Err(TOO_LONG));
        assert_eq!(convert("encode", &"a".repeat(8193)), Err(TOO_LONG));
    }
}
