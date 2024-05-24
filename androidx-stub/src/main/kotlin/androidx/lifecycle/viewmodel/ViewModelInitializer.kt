package androidx.lifecycle.viewmodel

import androidx.lifecycle.ViewModel

class ViewModelInitializer<T : ViewModel>(
    internal val clazz: Class<T>,
    internal val initializer: CreationExtras.() -> T,
)