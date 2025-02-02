package com.danilo.filamentdanilo.di

import com.danilo.filamentdanilo.presentation.screens.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::HomeViewModel)
}
