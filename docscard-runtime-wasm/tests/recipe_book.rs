use docscard_runtime_wasm::{CardPackage, ScriptedUiPresenter, UiRenderHost};
use serde_json::{json, Value};

// Native card unit tests don't exercise guest heap ownership across successive host replies.
#[test]
fn shipped_recipe_package_searches_scales_and_returns() {
    let package =
        CardPackage::open(include_bytes!("../examples/recipe_book/recipe_book.kkcard").to_vec())
            .unwrap();
    let host = UiRenderHost::new(ScriptedUiPresenter::new([
        json!({"event":"search","values":{"query":"tomato"}}),
        json!({"event":"open:tomato-egg"}),
        json!({"event":"scale","values":{"servings":"4 人"}}),
        json!({"event":"close_recipe"}),
        json!({"event":"close_book"}),
    ]));
    let mut runtime = package.into_runtime(host).unwrap();
    runtime.init(&package.manifest_json).unwrap();
    let result = runtime.handle(r#"{"type":"run","payload":{}}"#).unwrap();
    assert_eq!(
        serde_json::from_str::<Value>(&result).unwrap(),
        json!({"closed":true})
    );
    let screens = runtime.host().requests();
    assert_eq!(screens.len(), 5);
    assert_eq!(screens[1].tree, screens[4].tree);
    let scaled = screens[3].tree.as_ref().unwrap().to_string();
    assert!(scaled.contains("600 克"));
    assert!(scaled.contains("4 个"));
    runtime.shutdown().unwrap();
}
