package com.pashe.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pashe.app.Graph
import com.pashe.app.alarm.AlarmScheduler
import com.pashe.app.alarm.SyncWorker
import com.pashe.app.domain.AppMode
import com.pashe.app.ui.child.ChildHomeScreen
import com.pashe.app.ui.child.ChildSettingsScreen
import com.pashe.app.ui.child.ChildSignInScreen
import com.pashe.app.ui.child.HistoryScreen
import com.pashe.app.ui.child.MedicineEditScreen
import com.pashe.app.ui.child.ParentDetailScreen
import com.pashe.app.ui.child.ParentEditScreen
import com.pashe.app.ui.onboarding.DisclaimerScreen
import com.pashe.app.ui.onboarding.ModeSelectScreen
import com.pashe.app.ui.parent.PairingScreen
import com.pashe.app.ui.parent.ParentHomeScreen
import com.pashe.app.ui.parent.PermissionsScreen

object Routes {
    const val DISCLAIMER = "disclaimer"
    const val MODE = "mode"
    const val CHILD_SIGN_IN = "child/signin"
    const val CHILD_HOME = "child/home"
    const val CHILD_SETTINGS = "child/settings"
    const val PARENT_NEW = "child/add-parent"
    const val PARENT_DETAIL = "child/parent/{parentId}"
    const val PARENT_EDIT = "child/parent/{parentId}/edit"
    const val MEDICINE_EDIT = "child/parent/{parentId}/medicine?medId={medId}"
    const val HISTORY = "child/parent/{parentId}/history"
    const val PAIR = "parent/pair"
    const val PERMISSIONS = "parent/permissions"
    const val PARENT_HOME = "parent/home"

    fun parentDetail(id: String) = "child/parent/$id"
    fun parentEdit(id: String) = "child/parent/$id/edit"
    fun medicineEdit(parentId: String, medId: String? = null) =
        "child/parent/$parentId/medicine" + (medId?.let { "?medId=$it" } ?: "")
    fun history(id: String) = "child/parent/$id/history"
}

private fun startRoute(): String {
    val store = Graph.store
    return when {
        !store.disclaimerAccepted -> Routes.DISCLAIMER
        store.appMode == null -> Routes.MODE
        store.appMode == AppMode.CHILD -> if (Graph.auth.isChildSignedIn) Routes.CHILD_HOME else Routes.CHILD_SIGN_IN
        store.parentId == null -> Routes.PAIR
        else -> Routes.PARENT_HOME
    }
}

private fun NavHostController.replaceAll(route: String) = navigate(route) {
    popUpTo(0) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun PasheNavHost(openParentId: String?) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val start = remember { startRoute() }

    LaunchedEffect(openParentId) {
        if (openParentId != null && start == Routes.CHILD_HOME) nav.navigate(Routes.parentDetail(openParentId))
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(onAccept = {
                Graph.store.disclaimerAccepted = true
                nav.replaceAll(startRoute())
            })
        }
        composable(Routes.MODE) {
            ModeSelectScreen(
                onChild = {
                    Graph.store.appMode = AppMode.CHILD
                    nav.replaceAll(startRoute())
                },
                onParent = {
                    Graph.store.appMode = AppMode.PARENT
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("bn"))
                    nav.replaceAll(startRoute())
                },
            )
        }

        // --- Child ---
        composable(Routes.CHILD_SIGN_IN) {
            ChildSignInScreen(
                onSignedIn = { nav.replaceAll(Routes.CHILD_HOME) },
                onBack = {
                    Graph.store.appMode = null
                    nav.replaceAll(Routes.MODE)
                },
            )
        }
        composable(Routes.CHILD_HOME) {
            ChildHomeScreen(
                onOpenParent = { nav.navigate(Routes.parentDetail(it)) },
                onAddParent = { nav.navigate(Routes.PARENT_NEW) },
                onSettings = { nav.navigate(Routes.CHILD_SETTINGS) },
            )
        }
        composable(Routes.CHILD_SETTINGS) {
            ChildSettingsScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = {
                    Graph.store.appMode = null
                    nav.replaceAll(Routes.MODE)
                },
            )
        }
        composable(Routes.PARENT_NEW) {
            ParentEditScreen(parentId = null, onBack = { nav.popBackStack() }, onSaved = { id ->
                nav.popBackStack()
                nav.navigate(Routes.parentDetail(id))
            })
        }
        composable(Routes.PARENT_DETAIL, arguments = listOf(navArgument("parentId") { type = NavType.StringType })) { entry ->
            val parentId = entry.arguments!!.getString("parentId")!!
            ParentDetailScreen(
                parentId = parentId,
                onBack = { nav.popBackStack() },
                onEditParent = { nav.navigate(Routes.parentEdit(parentId)) },
                onAddMedicine = { nav.navigate(Routes.medicineEdit(parentId)) },
                onEditMedicine = { nav.navigate(Routes.medicineEdit(parentId, it)) },
                onHistory = { nav.navigate(Routes.history(parentId)) },
                onDeleted = { nav.popBackStack(Routes.CHILD_HOME, inclusive = false) },
            )
        }
        composable(Routes.PARENT_EDIT, arguments = listOf(navArgument("parentId") { type = NavType.StringType })) { entry ->
            ParentEditScreen(
                parentId = entry.arguments!!.getString("parentId"),
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(
            Routes.MEDICINE_EDIT,
            arguments = listOf(
                navArgument("parentId") { type = NavType.StringType },
                navArgument("medId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            MedicineEditScreen(
                parentId = entry.arguments!!.getString("parentId")!!,
                medId = entry.arguments!!.getString("medId"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.HISTORY, arguments = listOf(navArgument("parentId") { type = NavType.StringType })) { entry ->
            HistoryScreen(parentId = entry.arguments!!.getString("parentId")!!, onBack = { nav.popBackStack() })
        }

        // --- Parent ---
        composable(Routes.PAIR) {
            PairingScreen(
                onPaired = { nav.replaceAll(Routes.PERMISSIONS) },
                onWrongMode = {
                    Graph.store.appMode = null
                    nav.replaceAll(Routes.MODE)
                },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onDone = {
                AlarmScheduler.rescheduleAll(context)
                SyncWorker.schedulePeriodic(context)
                nav.replaceAll(Routes.PARENT_HOME)
            })
        }
        composable(Routes.PARENT_HOME) {
            ParentHomeScreen(onFixPermissions = { nav.navigate(Routes.PERMISSIONS) })
        }
    }
}
