package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.DealCategory
import com.example.model.USMerchant
import com.example.model.UserPreferenceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.worthItPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "worthit_algorithm_preferences"
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val PREFERRED_CATEGORIES = stringSetPreferencesKey("preferred_categories")
        val PREFERRED_MERCHANTS = stringSetPreferencesKey("preferred_merchants")
        val MIN_DISCOUNT_THRESHOLD = intPreferencesKey("min_discount_threshold")
        val ONLY_HISTORIC_LOWS = booleanPreferencesKey("only_historic_lows")
        val MAX_BUDGET_USD = doublePreferencesKey("max_budget_usd")
        val WATCHED_KEYWORDS = stringPreferencesKey("watched_keywords_csv")
        val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications_enabled")
        val AUTO_COPY_COUPONS = booleanPreferencesKey("auto_copy_coupons")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val WEIGHT_DISCOUNT = floatPreferencesKey("weight_discount")
        val WEIGHT_HISTORIC = floatPreferencesKey("weight_historic")
        val WEIGHT_SOCIAL = floatPreferencesKey("weight_social")
        val WEIGHT_PERSONAL = floatPreferencesKey("weight_personal")
    }

    val userProfileFlow: Flow<UserPreferenceProfile> = context.worthItPrefsDataStore.data.map { prefs ->
        val defaultProfile = UserPreferenceProfile()

        val categories = prefs[Keys.PREFERRED_CATEGORIES]
            ?.mapNotNull { name -> DealCategory.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultProfile.preferredCategories

        val merchants = prefs[Keys.PREFERRED_MERCHANTS]
            ?.mapNotNull { name -> USMerchant.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultProfile.preferredMerchants

        val keywords = prefs[Keys.WATCHED_KEYWORDS]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: defaultProfile.watchedKeywords

        UserPreferenceProfile(
            preferredCategories = categories,
            preferredMerchants = merchants,
            minimumDiscountThreshold = prefs[Keys.MIN_DISCOUNT_THRESHOLD] ?: defaultProfile.minimumDiscountThreshold,
            onlyHistoricLows = prefs[Keys.ONLY_HISTORIC_LOWS] ?: defaultProfile.onlyHistoricLows,
            maxBudgetUSD = prefs[Keys.MAX_BUDGET_USD] ?: defaultProfile.maxBudgetUSD,
            watchedKeywords = keywords,
            pushNotificationsEnabled = prefs[Keys.PUSH_NOTIFICATIONS] ?: defaultProfile.pushNotificationsEnabled,
            autoCopyCoupons = prefs[Keys.AUTO_COPY_COUPONS] ?: defaultProfile.autoCopyCoupons,
            themeMode = AppThemeMode.fromNameSafe(prefs[Keys.APP_THEME_MODE]),
            discountWeight = prefs[Keys.WEIGHT_DISCOUNT] ?: defaultProfile.discountWeight,
            historicLowWeight = prefs[Keys.WEIGHT_HISTORIC] ?: defaultProfile.historicLowWeight,
            socialWorthWeight = prefs[Keys.WEIGHT_SOCIAL] ?: defaultProfile.socialWorthWeight,
            personalAffinityWeight = prefs[Keys.WEIGHT_PERSONAL] ?: defaultProfile.personalAffinityWeight
        )
    }

    suspend fun saveProfile(profile: UserPreferenceProfile) {
        context.worthItPrefsDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_CATEGORIES] = profile.preferredCategories.map { it.name }.toSet()
            prefs[Keys.PREFERRED_MERCHANTS] = profile.preferredMerchants.map { it.name }.toSet()
            prefs[Keys.MIN_DISCOUNT_THRESHOLD] = profile.minimumDiscountThreshold
            prefs[Keys.ONLY_HISTORIC_LOWS] = profile.onlyHistoricLows
            prefs[Keys.MAX_BUDGET_USD] = profile.maxBudgetUSD
            prefs[Keys.WATCHED_KEYWORDS] = profile.watchedKeywords.joinToString("|")
            prefs[Keys.PUSH_NOTIFICATIONS] = profile.pushNotificationsEnabled
            prefs[Keys.AUTO_COPY_COUPONS] = profile.autoCopyCoupons
            prefs[Keys.APP_THEME_MODE] = profile.themeMode.name
            prefs[Keys.WEIGHT_DISCOUNT] = profile.discountWeight
            prefs[Keys.WEIGHT_HISTORIC] = profile.historicLowWeight
            prefs[Keys.WEIGHT_SOCIAL] = profile.socialWorthWeight
            prefs[Keys.WEIGHT_PERSONAL] = profile.personalAffinityWeight
        }
    }

    suspend fun saveThemeMode(themeMode: AppThemeMode) {
        context.worthItPrefsDataStore.edit { prefs ->
            prefs[Keys.APP_THEME_MODE] = themeMode.name
        }
    }
}
