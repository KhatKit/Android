package heizige.kk.khatkit.app.feature.chat.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import heizige.kk.khatkit.app.core.data.ai.tools.CardChatGateway
import heizige.kk.khatkit.app.feature.chat.helper.ChatManager
import javax.inject.Singleton

/** core -> feature 的反向依赖由这里单向绑定：feature 实现 core 端口。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatGatewayModule {
    @Binds
    @Singleton
    abstract fun bindCardChatGateway(impl: ChatManager): CardChatGateway
}
