package androidx.lifecycle

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.ViewModelInitializer

@Suppress("unused")
public open class ViewModelProvider {

    public interface Factory {
        /**
         * Creates a new instance of the given `Class`.
         *
         * Default implementation throws [UnsupportedOperationException].
         *
         * @param modelClass a `Class` whose instance is requested
         * @return a newly created ViewModel
         */
        public fun <T : ViewModel> create(modelClass: Class<T>): T {
            TODO("STUB")
        }

        /**
         * Creates a new instance of the given `Class`.
         *
         * @param modelClass a `Class` whose instance is requested
         * @param extras an additional information for this creation request
         * @return a newly created ViewModel
         */
        public fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            TODO("STUB")
        }

        companion object {
            /**
             * Creates an [InitializerViewModelFactory] using the given initializers.
             *
             * @param initializers the class initializer pairs used for the factory to create
             * simple view models
             */
            @JvmStatic
            fun from(vararg initializers: ViewModelInitializer<*>): Factory =
                TODO("STUB")
        }
    }

}