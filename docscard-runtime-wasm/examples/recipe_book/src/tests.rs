use super::*;
use std::collections::HashSet;

fn headless(args: Value) -> Result<Value, String> {
    run(json!({"type":"run","payload":args}), |_| {
        panic!("unexpected UI")
    })
}

#[test]
fn catalog_has_distinct_ids_and_complete_recipes() {
    assert_eq!(
        RECIPES.iter().map(|r| r.id).collect::<HashSet<_>>().len(),
        RECIPES.len()
    );
    let all = headless(json!({"query":""})).unwrap();
    assert_eq!(all["count"], RECIPES.len());
    for row in all["recipes"].as_array().unwrap() {
        let detail = headless(json!({"recipe_id":row["id"]})).unwrap();
        assert_eq!(detail["servings"], 2);
        assert!(detail["ingredients"].as_array().unwrap().len() >= 3);
        assert!(detail["steps"].as_array().unwrap().len() >= 3);
    }
}

#[test]
fn search_matches_names_aliases_and_ingredients() {
    for query in ["番茄，鸡蛋", "西红柿 鸡蛋", "tOmAtO egg"] {
        let result = headless(json!({"query":query})).unwrap();
        assert_eq!(result["count"], 1);
        assert_eq!(result["recipes"][0]["id"], "tomato-egg");
    }
    assert_eq!(headless(json!({"query":"干面条"})).unwrap()["count"], 2);
    assert_eq!(headless(json!({"query":"巧克力"})).unwrap()["count"], 0);
}

#[test]
fn quantity_scales_both_up_and_down_without_changing_units_or_steps() {
    let one = headless(json!({"recipe_id":"tomato-egg","servings":1})).unwrap();
    let four = headless(json!({"recipe_id":"tomato-egg","servings":4})).unwrap();
    assert_eq!(
        one["ingredients"][0],
        json!({"name":"番茄","amount":150,"unit":"克"})
    );
    assert_eq!(four["ingredients"][0]["amount"], 600);
    assert_eq!(
        four["ingredients"][1],
        json!({"name":"鸡蛋","amount":4,"unit":"个"})
    );
    assert_eq!(four["steps"], one["steps"]);
}

#[test]
fn invalid_parameters_never_open_ui() {
    for args in [
        json!({"query":null}),
        json!({"query":"番".repeat(101)}),
        json!({"recipe_id":"missing"}),
        json!({"query":"","recipe_id":"tomato-egg"}),
        json!({"servings":1}),
        json!(true),
    ] {
        assert!(headless(args).is_err());
    }
    for n in [
        json!(0),
        json!(5),
        json!(-1),
        json!(1.5),
        json!("2"),
        Value::Null,
    ] {
        assert!(headless(json!({"recipe_id":"tomato-egg","servings":n})).is_err());
    }
}

fn replay(replies: Vec<Value>) -> Vec<Value> {
    let mut replies = replies.into_iter();
    let mut pages = vec![];
    let result = run(json!({"type":"run","payload":{}}), |p| {
        pages.push(p);
        Ok(replies.next().expect("unexpected page"))
    })
    .unwrap();
    assert_eq!(result["closed"], true);
    assert!(replies.next().is_none());
    pages
}

#[test]
fn servings_refreshes_same_layer_and_back_preserves_the_filtered_list() {
    let pages = replay(vec![
        json!({"event":"search","values":{"query":"tomato egg"}}),
        json!({"event":"open:tomato-egg"}),
        json!({"event":"scale","values":{"servings":"4 人"}}),
        json!({"event":"close_recipe"}),
        json!({"event":"close_book"}),
    ]);
    assert!(pages
        .iter()
        .all(|p| p["method"] == "ui.screen" && p["args"][2]["layer"] == true));
    assert_eq!(pages[2]["args"][2]["push"], true);
    assert_eq!(pages[3]["args"][2]["push"], false);
    assert!(pages[2]["args"][1].to_string().contains("300 克"));
    assert!(pages[3]["args"][1].to_string().contains("600 克"));
    assert_eq!(pages[1], pages[4]);
}

#[test]
fn no_results_can_be_cleared_and_cancel_closes_at_any_depth() {
    let pages = replay(vec![
        json!({"event":"search","values":{"query":"蓝莓"}}),
        json!({"event":"clear"}),
        Value::Null,
    ]);
    assert!(pages[1].to_string().contains("没有找到匹配的菜谱"));
    assert_eq!(pages[0], pages[2]);
    assert_eq!(
        replay(vec![json!({"event":"open:tofu-mushroom"}), Value::Null]).len(),
        2
    );
}

#[test]
fn widget_stays_inline_and_requests_no_additional_capabilities() {
    let result = run(json!({"type":"widget"}), |p| {
        assert_eq!(p["method"], "ui.show");
        assert_eq!(p["args"][1]["frame"], "header");
        Ok(Value::Null)
    })
    .unwrap();
    assert_eq!(result["ok"], true);
    let m: Value = serde_json::from_str(include_str!("../manifest.json")).unwrap();
    assert_eq!(m["capabilities"], json!(["ui:render"]));
}
