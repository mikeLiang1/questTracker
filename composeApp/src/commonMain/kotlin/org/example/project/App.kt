package org.example.project

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.example.budget.ui.theme.BudgetTheme
import org.example.project.navigation.AppNavigation
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun App() {
    BudgetTheme {
        Surface {
            AppNavigation()
        }
    }
}
