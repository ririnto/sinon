package com.ririnto.sinon.harness.ktlint

internal class ImportOverFqnShadowedReceiverFixture {
    object WidgetRoot {
        const val WIDGET: Int = 1
    }

    class FooHolder {
        val foo: WidgetRoot = WidgetRoot
    }

    fun receiverChain(): Int {
        val com: FooHolder = FooHolder()
        return com.foo.WIDGET
    }
}
