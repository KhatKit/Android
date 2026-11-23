use serde_json::{json, Value};

pub struct Recipe {
    pub id: &'static str,
    pub name: &'static str,
    pub minutes: u16,
    pub aliases: &'static str,
    // Each quantity is per serving; keep units unchanged when scaling.
    pub ingredients: &'static [(&'static str, u16, &'static str)],
    pub steps: &'static [&'static str],
}

pub const RECIPES: &[Recipe] = &[
    Recipe {
        id: "tomato-egg",
        name: "番茄炒蛋",
        minutes: 15,
        aliases: "西红柿炒鸡蛋 tomato egg",
        ingredients: &[
            ("番茄", 150, "克"),
            ("鸡蛋", 1, "个"),
            ("食用油", 5, "毫升"),
            ("盐", 1, "克"),
        ],
        steps: &[
            "番茄洗净切成小块，鸡蛋加少许盐打散。",
            "锅里放一半油，中火翻炒蛋液至凝固，盛出。",
            "余油炒软番茄，用锅铲压出汁。倒回鸡蛋，加剩余的盐翻匀，炒至蛋块全熟。",
        ],
    },
    Recipe {
        id: "tofu-mushroom",
        name: "香菇烧豆腐",
        minutes: 25,
        aliases: "豆腐煲 tofu mushroom",
        ingredients: &[
            ("老豆腐", 160, "克"),
            ("鲜香菇", 60, "克"),
            ("生抽", 5, "毫升"),
            ("食用油", 5, "毫升"),
            ("水", 80, "毫升"),
        ],
        steps: &[
            "豆腐切厚片，香菇去根切片。",
            "锅里放油，将豆腐两面煎至浅金色，拨到一旁。",
            "加入香菇炒软，倒入水和生抽，小火焖至豆腐入味，揭盖稍微收汁。",
        ],
    },
    Recipe {
        id: "scallion-noodles",
        name: "葱油拌面",
        minutes: 20,
        aliases: "香葱面 scallion noodles",
        ingredients: &[
            ("干面条", 90, "克"),
            ("小葱", 15, "克"),
            ("食用油", 8, "毫升"),
            ("生抽", 8, "毫升"),
            ("糖", 2, "克"),
        ],
        steps: &[
            "葱切段，葱白和葱绿分开。",
            "油和葱白一同入锅，小火煎香后加葱绿。葱段边缘变金黄便关火，稍凉后拌入生抽和糖。",
            "另起锅把面条煮熟，沥干后趁热拌上葱油汁。",
        ],
    },
    Recipe {
        id: "potato-pepper",
        name: "青椒土豆丝",
        minutes: 20,
        aliases: "青椒炒土豆 potato pepper",
        ingredients: &[
            ("土豆", 160, "克"),
            ("青椒", 45, "克"),
            ("米醋", 4, "毫升"),
            ("食用油", 5, "毫升"),
            ("盐", 1, "克"),
        ],
        steps: &[
            "土豆去皮切细丝，清水冲去表面淀粉并沥干。青椒去籽切丝。",
            "热锅加油，先炒土豆丝，沿锅边加少许水使其均匀受热。",
            "土豆丝熟透后放青椒，炒至断生，沿锅边烹入米醋，加盐翻匀。",
        ],
    },
    Recipe {
        id: "pumpkin-porridge",
        name: "南瓜小米粥",
        minutes: 35,
        aliases: "早餐 pumpkin millet porridge",
        ingredients: &[("小米", 35, "克"), ("南瓜", 90, "克"), ("水", 350, "毫升")],
        steps: &[
            "小米淘洗，南瓜去皮去瓤后切小丁。",
            "锅中水烧开，放入小米和南瓜，搅动锅底。",
            "转小火留缝煮约 25 分钟，期间搅动几次；南瓜可压碎，粥变稠即可。",
        ],
    },
    Recipe {
        id: "garlic-broccoli",
        name: "蒜蓉西兰花",
        minutes: 15,
        aliases: "绿花椰菜 garlic broccoli",
        ingredients: &[
            ("西兰花", 160, "克"),
            ("大蒜", 5, "克"),
            ("食用油", 5, "毫升"),
            ("盐", 1, "克"),
        ],
        steps: &[
            "西兰花切成相近大小的小朵，冲洗干净。蒜瓣切末。",
            "西兰花放入沸水焯至茎部能用筷子戳入，捞出沥水。",
            "热油用小火炒香蒜末，放入西兰花和盐，翻匀出锅。",
        ],
    },
    Recipe {
        id: "tomato-noodles",
        name: "番茄汤面",
        minutes: 20,
        aliases: "西红柿面 tomato noodles",
        ingredients: &[
            ("番茄", 150, "克"),
            ("干面条", 80, "克"),
            ("小葱", 5, "克"),
            ("食用油", 4, "毫升"),
            ("盐", 1, "克"),
            ("水", 400, "毫升"),
        ],
        steps: &[
            "番茄切丁，葱白和葱绿分别切碎。",
            "油热后炒香葱白，加入番茄和盐炒至出汁，倒水煮开。",
            "加入面条，用筷子拨散，按面条粗细煮至熟透，盛碗撒葱绿。",
        ],
    },
    Recipe {
        id: "cucumber-salad",
        name: "凉拌黄瓜",
        minutes: 10,
        aliases: "拍黄瓜 cucumber salad",
        ingredients: &[
            ("黄瓜", 150, "克"),
            ("大蒜", 3, "克"),
            ("米醋", 5, "毫升"),
            ("生抽", 4, "毫升"),
            ("香油", 2, "毫升"),
        ],
        steps: &[
            "黄瓜洗净擦干，轻拍裂开，切成一口大小。",
            "蒜瓣压成泥，与米醋、生抽和香油拌成汁。",
            "上桌前把料汁拌入黄瓜，翻拌到每块都裹上即可。",
        ],
    },
    Recipe {
        id: "corn-fried-rice",
        name: "玉米蛋炒饭",
        minutes: 15,
        aliases: "黄金炒饭 corn egg fried rice",
        ingredients: &[
            ("熟米饭", 150, "克"),
            ("熟玉米粒", 50, "克"),
            ("鸡蛋", 1, "个"),
            ("小葱", 4, "克"),
            ("食用油", 5, "毫升"),
            ("盐", 1, "克"),
        ],
        steps: &[
            "米饭拨散，鸡蛋打散，葱切碎。",
            "锅中加油，把蛋液炒至凝固，加入玉米粒翻炒。",
            "倒入米饭，用铲背轻压散开，炒至整锅热透后加葱花和盐。",
        ],
    },
    Recipe {
        id: "carrot-oat",
        name: "胡萝卜燕麦饼",
        minutes: 20,
        aliases: "早餐 carrot oat pancake",
        ingredients: &[
            ("胡萝卜", 60, "克"),
            ("即食燕麦片", 35, "克"),
            ("鸡蛋", 1, "个"),
            ("水", 20, "毫升"),
            ("食用油", 4, "毫升"),
        ],
        steps: &[
            "胡萝卜擦细丝，与燕麦、鸡蛋和水拌匀，静置 5 分钟。",
            "平底锅刷油，把面糊分成小份摊薄，小火煎至底面定形。",
            "翻面继续煎至两面金黄，掰开中间没有湿蛋糊再出锅。",
        ],
    },
];

impl Recipe {
    pub fn summary(&self) -> Value {
        json!({"id":self.id,"name":self.name,"minutes":self.minutes})
    }
    pub fn detail(&self, servings: u8) -> Value {
        let mut value = self.summary();
        value["servings"] = json!(servings);
        value["ingredients"] = self.ingredients.iter().map(|(name, amount, unit)|
            json!({"name":name,"amount":amount * u16::from(servings),"unit":unit})).collect();
        value["steps"] = json!(self.steps);
        value
    }
}

pub fn lookup(id: &str) -> Option<&'static Recipe> {
    RECIPES.iter().find(|r| r.id == id)
}

pub fn search(query: &str) -> Result<Vec<&'static Recipe>, String> {
    if query.chars().count() > 100 {
        return Err("搜索词最多 100 个字符".into());
    }
    let query = query.to_lowercase();
    let terms: Vec<_> = query
        .split(|c: char| c.is_whitespace() || matches!(c, '、' | ',' | '，'))
        .filter(|s| !s.is_empty())
        .collect();
    Ok(RECIPES
        .iter()
        .filter(|r| {
            terms.iter().all(|term| {
                r.name.contains(term)
                    || r.aliases.contains(term)
                    || r.ingredients.iter().any(|(n, _, _)| n.contains(term))
            })
        })
        .collect())
}
