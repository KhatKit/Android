package heizige.kk.khatkit.app.core.data.datastore

import androidx.compose.material3.Text
import heizige.kk.khatkit.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

/**
 * 推荐的提供商列表，在提供商设置页右上角的推荐 Sheet 中展示。
 */
val RECOMMENDED_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("7e2a9c1e-6b5f-4a3d-9c8e-1f2b3c4d5e6f"),
        name = "KhatKit 套餐网关",
        baseUrl = "https://heizige.top/v1",
        apiKey = "",
        enabled = false,
        description = {
            Text(
                text = "KhatKitHub 的 OpenAI 兼容套餐网关（默认 https://heizige.top/v1）。\n" +
                    "使用前请先在「设置 → 套餐 / 激活」中激活套餐：激活后可在该页一键创建/更新本供应商，" +
                    "API Key 使用激活令牌。本预设默认不启用，也不会自动选中，需要时请到「服务商」中添加模型并开启。"
            )
        },
    ),
)
