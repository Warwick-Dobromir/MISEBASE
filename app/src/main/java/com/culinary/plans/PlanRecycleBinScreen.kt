package com.culinary.plans

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private object PlanRecyclePassword {
    // 修改密码只能修改源码中的盐值/摘要并重新构建；应用内不提供修改入口。
    private const val ITERATIONS = 310_000
    private const val SALT = "wAz7pyscChxkZU5ZoVNUsg=="
    private const val HASH = "t47B1NDKFfluA172JdwY16JSE8IIFclk0UDqsDdvxHM="

    fun verify(candidate: String): Boolean {
        val spec = PBEKeySpec(candidate.toCharArray(), Base64.decode(SALT, Base64.DEFAULT), ITERATIONS, 256)
        return try {
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            MessageDigest.isEqual(actual, Base64.decode(HASH, Base64.DEFAULT))
        } finally {
            spec.clearPassword()
        }
    }
}

@Composable
fun PlanRecycleBinRoute(
    onBack: () -> Unit,
    viewModel: PlanManagementViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val completed = plans.filter { it.completed }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("计划回收站", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("制作计划与工作计划独立归档", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text("制作计划", style = MaterialTheme.typography.titleMedium)
        RecyclePlanGroup(completed.filter { it.kind == PlanKind.PRODUCTION }, onRestore = viewModel::restore) { plan ->
            if (PlanRecyclePassword.verify(password)) {
                viewModel.permanentlyDeleteAfterPassword(plan)
                password = ""
                error = null
            } else error = "密码不正确"
        }
        Spacer(Modifier.height(22.dp))
        Text("工作计划", style = MaterialTheme.typography.titleMedium)
        RecyclePlanGroup(completed.filter { it.kind == PlanKind.WORK }, onRestore = viewModel::restore) { plan ->
            if (PlanRecyclePassword.verify(password)) {
                viewModel.permanentlyDeleteAfterPassword(plan)
                password = ""
                error = null
            } else error = "密码不正确"
        }
        Spacer(Modifier.height(24.dp))
        Text("永久删除验证", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入回收站密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(16.dp))
        Text("密码只能通过修改源码中的验证摘要并重新构建软件来更换。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun RecyclePlanGroup(
    items: List<PlanUiItem>,
    onRestore: (PlanUiItem) -> Unit,
    onDelete: (PlanUiItem) -> Unit,
) {
    if (items.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("暂无已完成计划", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else items.forEach { plan ->
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plan.title, style = MaterialTheme.typography.bodyLarge)
            plan.plannedDate?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row {
                TextButton(onClick = { onRestore(plan) }) { Text("恢复") }
                TextButton(onClick = { onDelete(plan) }) { Text("永久删除") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
