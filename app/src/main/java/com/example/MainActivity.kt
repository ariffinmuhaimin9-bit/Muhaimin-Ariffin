package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Book
import com.example.data.LibraryRepository
import com.example.data.Loan
import com.example.data.User
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundColor)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) { innerPadding ->
                    LibraryApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { LibraryRepository(context) }
    
    // Core data streams
    val users by repository.users.collectAsState()
    val books by repository.books.collectAsState()
    val loans by repository.loans.collectAsState()
    val fineRate by repository.fineRate.collectAsState()

    // Login Form State
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf("") }
    var currentUserName by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("Student") } // "Admin", "Library Staff", "Teacher", "Student"

    // Inputs for Login
    var inputUserId by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var selectedLoginRole by remember { mutableStateOf("Student") }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordLogin by remember { mutableStateOf(false) }

    // Dashboard Navigation (Tab Indices)
    // Index 0: Overview / Interaction desk
    // Index 1: Live Catalog Search
    // Index 2: Registration Panel (Admin Only) or Personal Loan History (Teacher/Student)
    // Index 3: System Admin / App Config
    var activeTab by remember { mutableIntStateOf(0) }

    // Books Module Search & Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }

    // Borrow Form Inputs (Admin/Staff only)
    var bUserId by remember { mutableStateOf("") }
    var bBookId by remember { mutableStateOf("") }
    var bDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var bSuccessMsg by remember { mutableStateOf<String?>(null) }
    var bErrorMsg by remember { mutableStateOf<String?>(null) }

    // Return Form Inputs (Admin/Staff only)
    var rLoanIdOrBookId by remember { mutableStateOf("") }
    var rDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var rSuccessMsg by remember { mutableStateOf<String?>(null) }
    var rErrorMsg by remember { mutableStateOf<String?>(null) }

    // User Registration Inputs (Admin only)
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("Student") }
    var regCustomId by remember { mutableStateOf("") }
    var regPassword_ by remember { mutableStateOf("") }
    var regSuccessMsg by remember { mutableStateOf<String?>(null) }
    var regErrorMsg by remember { mutableStateOf<String?>(null) }

    // Book Management Form (Admin only)
    var addBookTitle by remember { mutableStateOf("") }
    var addBookAuthor by remember { mutableStateOf("") }
    var addBookIsbn by remember { mutableStateOf("") }
    var addBookCategory by remember { mutableStateOf("Fiction") }
    var addBookQty by remember { mutableStateOf("3") }
    var addSuccessMsg by remember { mutableStateOf<String?>(null) }
    var addErrorMsg by remember { mutableStateOf<String?>(null) }

    // App Fine Modifier (Admin only)
    var editFineTextField by remember { mutableStateOf(String.format("%.2f", fineRate)) }

    // Dynamic Computations
    val activeLoans = loans.filter { it.returnDateString == null }
    val overdueLoans = loans.filter { loan ->
        if (loan.returnDateString != null) return@filter false
        try {
            val d1 = LocalDate.parse(loan.borrowDateString)
            val d2 = LocalDate.now()
            ChronoUnit.DAYS.between(d1, d2) > 14
        } catch (e: Exception) {
            false
        }
    }

    // Unpaid fines summary count
    val totalUnpaidFines = loans.filter { !it.finePaid }.sumOf { loan ->
        if (loan.returnDateString == null) {
            // Calculated dynamically
            try {
                val d1 = LocalDate.parse(loan.borrowDateString)
                val d2 = LocalDate.now()
                val days = ChronoUnit.DAYS.between(d1, d2)
                if (days > 14) (days - 14) * fineRate else 0.0
            } catch (e: Exception) {
                0.0
            }
        } else {
            loan.fineAmount
        }
    }

    // Auto update User Registration ID when naming changes
    LaunchedEffect(regName, regRole) {
        if (regName.isNotBlank()) {
            regCustomId = repository.generateUserId(regName, regRole)
        }
    }

    // Login process
    fun attemptLogin() {
        val trimmedId = inputUserId.trim().lowercase()
        val user = users.find { it.id.lowercase() == trimmedId }

        if (user == null) {
            loginErrorMessage = "ID Pengguna tidak dijumpai dlm sistem."
            return
        }

        if (user.passwordSecret != inputPassword) {
            loginErrorMessage = "Kata laluan salah. Sila cuba lagi."
            return
        }

        // --- Guardrail check: User Role must match drop-down role select ---
        if (user.role != selectedLoginRole) {
            loginErrorMessage = "Ralat Peranan: ID berada di bawah '${user.role}' tetapi pilihan role anda ialah '$selectedLoginRole'."
            return
        }

        // Logic check complete, login success
        isLoggedIn = true
        currentUserId = user.id
        currentUserName = user.name
        currentUserRole = user.role
        loginErrorMessage = null
        activeTab = 0 // Reset nav tab
        Toast.makeText(context, "Selamat Datang $currentUserName!", Toast.LENGTH_SHORT).show()
    }

    fun handleLogout() {
        isLoggedIn = false
        currentUserId = ""
        currentUserName = ""
        currentUserRole = "Student"
        inputPassword = ""
        loginErrorMessage = null
        activeTab = 0
    }

    // Main layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        if (!isLoggedIn) {
            // --- LOGIN PAGE ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Main St Mary Icon/Avatar Header
                Spacer(modifier = Modifier.height(30.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(PrimaryPurple)
                        .shadow(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SMK",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SMK St. Mary",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Library Management Portal",
                    fontSize = 14.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Login Form Container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, OutlineColor)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Log Masuk Portal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        // Role Selection Select / Pills row
                        Column {
                            Text(
                                text = "Pilih Peranan Anda",
                                fontSize = 12.sp,
                                color = TextGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(InputFieldBg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Admin", "Staff", "Teacher", "Student").forEach { roleName ->
                                    val dbRole = when(roleName) {
                                        "Staff" -> "Library Staff"
                                        else -> roleName
                                    }
                                    val isSelected = selectedLoginRole == dbRole
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) ActiveLoansBg else Color.Transparent)
                                            .clickable { selectedLoginRole = dbRole }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = roleName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) DarkPurple else TextGray
                                        )
                                    }
                                }
                            }
                        }

                        // User ID Input
                        Column {
                            Text(
                                text = "ID Pengguna",
                                fontSize = 12.sp,
                                color = TextGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = inputUserId,
                                onValueChange = { inputUserId = it },
                                placeholder = { Text("cth: admin / student", fontSize = 14.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("username_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = InputFieldBg,
                                    unfocusedContainerColor = InputFieldBg,
                                    focusedBorderColor = PrimaryPurple,
                                    unfocusedBorderColor = OutlineColor
                                ),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User", tint = PrimaryPurple) }
                            )
                        }

                        // Password Input
                        Column {
                            Text(
                                text = "Kata Laluan",
                                fontSize = 12.sp,
                                color = TextGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                placeholder = { Text("Sila masukkan kata laluan", fontSize = 14.sp) },
                                visualTransformation = if (showPasswordLogin) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = InputFieldBg,
                                    unfocusedContainerColor = InputFieldBg,
                                    focusedBorderColor = PrimaryPurple,
                                    unfocusedBorderColor = OutlineColor
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = PrimaryPurple) },
                                trailingIcon = {
                                    IconButton(onClick = { showPasswordLogin = !showPasswordLogin }) {
                                        Icon(
                                            imageVector = if (showPasswordLogin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle info",
                                            tint = TextGray
                                        )
                                    }
                                }
                            )
                        }

                        // Error Banner
                        loginErrorMessage?.let { message ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FinesBg)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Ralat", tint = FinesText)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    color = FinesText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = { attemptLogin() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("login_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "MASUK PORTAL",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Quick Login Cheatsheet Card (Sleek layout)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = InputFieldBg),
                    border = BorderStroke(1.dp, OutlineColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "💡 Panduan Log Masuk Segera (Demo):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val shortcuts = listOf(
                            Triple("admin", "Admin", "Admin"),
                            Triple("staff", "Library Staff", "Staff"),
                            Triple("teacher", "Teacher", "Teacher"),
                            Triple("student", "Student", "Student")
                        )

                        shortcuts.forEach { (usrId, roleDb, roleDisp) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        inputUserId = usrId
                                        inputPassword = "123"
                                        selectedLoginRole = roleDb
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "click", modifier = Modifier.size(12.dp), tint = PrimaryPurple)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$roleDisp Account",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDark
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$usrId / 123",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = DarkPurple
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        } else {
            // --- MAIN APP PORTAL DASHBOARD (Role-Based) ---
            
            // Header component matching user profile role
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "SMK St. Mary",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = when (currentUserRole) {
                                    "Admin" -> "Sistem Administrator Portal"
                                    "Library Staff" -> "Kaunter Pintar Perpustakaan"
                                    "Teacher" -> "Portal Guru — $currentUserName"
                                    else -> "Portal Murid — $currentUserName"
                                },
                                fontSize = 12.sp,
                                color = TextGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Logout Clickable Avatar Circle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LightPurple)
                                .clickable { handleLogout() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Log out icon",
                                tint = DarkPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(color = InputFieldBg, modifier = Modifier.padding(top = 10.dp))
            }

            // Central scrolling platform containing RBAC functions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> {
                        // --- NAVIGATION ITEM 0: DESK / OVERVIEW PANELS ---
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Metrics Grid Row
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Card 1: Active Loans
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = ActiveLoansBg),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "PINJAMAN AKTIF",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkPurple,
                                                letterSpacing = 0.5.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${activeLoans.size}",
                                                    fontSize = 32.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DarkPurple
                                                )
                                                // Small badge "+X today" or overdue list count
                                                if (overdueLoans.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(FinesBg)
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${overdueLoans.size} lewat",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF6B1111)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Card 2: Combined Fines (Total / Unpaid depending on role)
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = if (currentUserRole == "Student" && totalUnpaidFines > 0.0) FinesBg else FinesBg),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = if (currentUserRole == "Student" || currentUserRole == "Teacher") "DENDA PERIBADI" else "JUMLAH DENDA",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FinesText,
                                                letterSpacing = 0.5.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "RM " + String.format("%.2f", if (currentUserRole == "Student") {
                                                    // Filter student's active unpaid fines
                                                    loans.filter { it.userId.lowercase() == currentUserId.lowercase() && !it.finePaid }.sumOf { it.fineAmount }
                                                } else totalUnpaidFines),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FinesText
                                            )
                                        }
                                    }
                                }
                            }

                            // Conditional Role Actions layout
                            if (currentUserRole == "Admin" || currentUserRole == "Library Staff") {
                                // ADMIN & STAFF Desk Functions -> Borrow & Return Panels
                                item {
                                    Text(
                                        text = "Kaunter Pinjaman Buku (Admin/Staff)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                // Interactive Borrows Form Block
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "1. Kaunter Pinjaman Baru",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = PrimaryPurple
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(LightPurple)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Proses Keluar", fontSize = 10.sp, color = DarkPurple, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Form input grids
                                            OutlinedTextField(
                                                value = bUserId,
                                                onValueChange = { bUserId = it },
                                                label = { Text("ID Pengguna / Ahli", fontSize = 12.sp) },
                                                placeholder = { Text("cth: student / teacher") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))

                                            OutlinedTextField(
                                                value = bBookId,
                                                onValueChange = { bBookId = it },
                                                label = { Text("ID Buku (cth: B001)", fontSize = 12.sp) },
                                                placeholder = { Text("Masukkan kod ID buku") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Borrow Messages Banner
                                            bSuccessMsg?.let { msg ->
                                                Text(msg, color = Color(0xFF006A6A), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                            }
                                            bErrorMsg?.let { msg ->
                                                Text(msg, color = FinesText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                            }

                                            Button(
                                                onClick = {
                                                    val res = repository.borrowBook(bUserId, bBookId)
                                                    if (res.isSuccess) {
                                                        bSuccessMsg = "✅ Pinjaman berjaya didaftarkan! ID: ${res.getOrNull()?.id}"
                                                        bErrorMsg = null
                                                        bUserId = ""
                                                        bBookId = ""
                                                    } else {
                                                        bErrorMsg = "❌ Ralat: ${res.exceptionOrNull()?.message}"
                                                        bSuccessMsg = null
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("SAHKAN PINJAMAN", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Interactive Return Form Block
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "2. Pemulangan & Kira Denda",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF6B1111)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(FinesBg)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Proses Pulang", fontSize = 10.sp, color = FinesText, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))

                                            OutlinedTextField(
                                                value = rLoanIdOrBookId,
                                                onValueChange = { rLoanIdOrBookId = it },
                                                label = { Text("ID Pinjaman atau ID Buku", fontSize = 12.sp) },
                                                placeholder = { Text("cth: B001 atau L001") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Return Messages Banner
                                            rSuccessMsg?.let { msg ->
                                                Text(msg, color = Color(0xFF006A6A), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                            }
                                            rErrorMsg?.let { msg ->
                                                Text(msg, color = FinesText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                            }

                                            Button(
                                                onClick = {
                                                    val res = repository.returnBook(rLoanIdOrBookId)
                                                    if (res.isSuccess) {
                                                        val returnedLoan = res.getOrNull()
                                                        val penalty = returnedLoan?.fineAmount ?: 0.0
                                                        val lateMsg = if (penalty > 0.0) {
                                                            "Terlewat! Denda dikenakan: RM " + String.format("%.2f", penalty)
                                                        } else {
                                                            "Pemulangan tepat pada masa. Tiada denda."
                                                        }
                                                        rSuccessMsg = "✅ Buku dipulangkan! $lateMsg"
                                                        rErrorMsg = null
                                                        rLoanIdOrBookId = ""
                                                    } else {
                                                        rErrorMsg = "❌ Ralat: ${res.exceptionOrNull()?.message}"
                                                        rSuccessMsg = null
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1B20)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("PROSES PEMULANGAN BUKU", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Global summary of Active Outstandings (Visual Analytics List)
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Senarai Denda Belum Bayar",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text("${loans.count { !it.finePaid && it.fineAmount > 0.0 }} denda aktif", fontSize = 12.sp, color = TextGray)
                                    }
                                }

                                val unpaidFinesList = loans.filter { !it.finePaid && it.fineAmount > 0.0 }
                                if (unpaidFinesList.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White)
                                                .border(1.dp, OutlineColor, RoundedCornerShape(16.dp))
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "🎉 Tiada rekod denda aktif dlm sistem perpustakaan.",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF006A6A)
                                            )
                                        }
                                    }
                                } else {
                                    items(unpaidFinesList) { fineItem ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, OutlineColor),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = fineItem.userName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextDark
                                                    )
                                                    Text(
                                                        text = "Buku: ${fineItem.bookTitle} (${fineItem.bookId})",
                                                        fontSize = 11.sp,
                                                        color = TextGray
                                                    )
                                                    Text(
                                                        text = "Denda: RM ${String.format("%.2f", fineItem.fineAmount)} • Pinjam: ${fineItem.borrowDateString}",
                                                        fontSize = 11.sp,
                                                        color = FinesText,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                // Clear payment Button
                                                Button(
                                                    onClick = {
                                                        repository.markFineAsPaid(fineItem.id)
                                                        Toast.makeText(context, "Denda RM ${String.format("%.2f", fineItem.fineAmount)} dibayar!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                ) {
                                                    Text("Bayar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // TEACHER or STUDENT Dashboard View (Personal Logs)
                                item {
                                    Text(
                                        text = "Statistik Pinjaman & Tindakan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextDark,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "📜 Maklumat & Syarat Pusat Sumber",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = PrimaryPurple
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "1. Setiap pinjaman dihadkan selama 14 hari.",
                                                fontSize = 12.sp,
                                                color = TextGray
                                            )
                                            Text(
                                                text = "2. Denda kelewatan dikenakan pada kadar RM " + String.format("%.2f", fineRate) + " sehari bagi setiap buku.",
                                                fontSize = 12.sp,
                                                color = TextGray
                                            )
                                            Text(
                                                text = if (currentUserRole == "Teacher") {
                                                    "3. Sebagai Guru, anda dibenarkan meminjam maksimum 5 buah buku serentak."
                                                } else {
                                                    "3. Sebagai Murid, anda dibenarkan meminjam maksimum 2 buah buku serentak."
                                                },
                                                fontSize = 12.sp,
                                                color = TextGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Pinjaman Aktif Anda",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextDark,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                val myActiveLoans = loans.filter { it.userId.lowercase() == currentUserId.lowercase() && it.returnDateString == null }
                                if (myActiveLoans.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White)
                                                .border(1.dp, OutlineColor, RoundedCornerShape(16.dp))
                                                .padding(30.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Tiada pinjaman buku aktif buat masa ini.",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextGray
                                            )
                                        }
                                    }
                                } else {
                                    items(myActiveLoans) { myLoan ->
                                        // Calculate dynamic days kept
                                        val daysKeptVal = try {
                                            val d1 = LocalDate.parse(myLoan.borrowDateString)
                                            val d2 = LocalDate.now()
                                            ChronoUnit.DAYS.between(d1, d2).coerceAtLeast(0)
                                        } catch (e: Exception) {
                                            0L
                                        }
                                        val isOverdue = daysKeptVal > 14
                                        val currentFine = if (isOverdue) (daysKeptVal - 14) * fineRate else 0.0

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = if (isOverdue) FinesBg else Color.White),
                                            border = BorderStroke(1.dp, OutlineColor),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        myLoan.bookTitle,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextDark,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text("Tarikh Pinjam: ${myLoan.borrowDateString}", fontSize = 11.sp, color = TextGray)
                                                    Text("Tempoh dipinjam: $daysKeptVal hari", fontSize = 11.sp, color = TextGray)
                                                }

                                                if (isOverdue) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF6B1111))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("LEWAT ${daysKeptVal-14} Hari", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text("RM " + String.format("%.2f", currentFine), color = FinesText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFE2F0D9))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Ada ${14 - daysKeptVal} hari", color = Color(0xFF385723), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(30.dp)) }
                        }
                    }

                    1 -> {
                        // --- NAVIGATION ITEM 1: GLOBAL CATALOG SEARCH & FILTER ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Katalog Buku & Carian",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Live Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari Tajuk, Penulis, atau ISBN...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = PrimaryPurple,
                                    unfocusedBorderColor = OutlineColor
                                ),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Sari Icon", tint = PrimaryPurple) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextGray)
                                        }
                                    }
                                },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Scrollable Category Row filters
                            val categories = listOf("Semua", "Fiction", "Drama", "History", "Science & Technology", "Literature")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSelected = selectedCategoryFilter == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) PrimaryPurple else Color.White)
                                            .border(1.dp, if (isSelected) PrimaryPurple else OutlineColor, RoundedCornerShape(20.dp))
                                            .clickable { selectedCategoryFilter = cat }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else TextGray
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search Books Filter Block
                            val filteredBooks = books.filter { book ->
                                val matchesSearch = book.title.contains(searchQuery, ignoreCase = true) ||
                                        book.author.contains(searchQuery, ignoreCase = true) ||
                                        book.isbn.contains(searchQuery, ignoreCase = true) ||
                                        book.id.contains(searchQuery, ignoreCase = true)
                                val matchesGenre = selectedCategoryFilter == "Semua" || book.category == selectedCategoryFilter
                                matchesSearch && matchesGenre
                            }

                            if (filteredBooks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("📖", fontSize = 48.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Tiada buku ditemui.",
                                            fontWeight = FontWeight.Bold,
                                            color = TextGray,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Cuba perkataan carian yang lain.",
                                            color = TextGray,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredBooks) { bookItem ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, OutlineColor),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Book Thumbnail placeholder
                                                Box(
                                                    modifier = Modifier
                                                        .size(50.dp, 70.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(LightPurple),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("📚", fontSize = 20.sp)
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = bookItem.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = TextDark,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(text = "Penulis: ${bookItem.author}", fontSize = 12.sp, color = TextGray)
                                                    Text(text = "ISBN: ${bookItem.isbn}", fontSize = 11.sp, color = TextGray, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(InputFieldBg)
                                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(bookItem.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkPurple)
                                                        }

                                                        if (bookItem.availableCopies > 0) {
                                                            Text(
                                                                text = "Tersedia (${bookItem.availableCopies}/${bookItem.quantity})",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = SuccessGreen
                                                            )
                                                        } else {
                                                            Text(
                                                                text = "Habis Dipinjam",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = FinesText
                                                            )
                                                        }
                                                    }
                                                }

                                                // Admin Delete option if logged in as Admin
                                                if (currentUserRole == "Admin") {
                                                    IconButton(onClick = {
                                                        val deletedNow = repository.deleteBook(bookItem.id)
                                                        if (deletedNow) {
                                                            Toast.makeText(context, "Buku berjaya dipadam!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Tak boleh padam! Buku sedang dipinjam.", Toast.LENGTH_LONG).show()
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete book", tint = FinesText)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // --- NAVIGATION ITEM 2: ADMIN USER GENERATOR -OR- USER PERSONAL HISTORY ---
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (currentUserRole == "Admin") {
                                // ADMIN EXCLUSIVE VIEW -> User Registration Desk with Auto-Generator
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Pendaftaran Ahli Baru (Admin Only)",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(PrimaryPurple)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("ADMIN", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // 1. Full Name
                                            Column {
                                                Text("Nama Penuh Ahli", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedTextField(
                                                    value = regName,
                                                    onValueChange = { regName = it },
                                                    placeholder = { Text("cth: Nurul Izzah", fontSize = 13.sp) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                            }

                                            // 2. Email Address
                                            Column {
                                                Text("Alamat Emel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedTextField(
                                                    value = regEmail,
                                                    onValueChange = { regEmail = it },
                                                    placeholder = { Text("cth: nurul@stmary.edu.my", fontSize = 13.sp) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                            }

                                            // 3. Selection Select Row & Code preview
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Pilihan Peranan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .border(1.dp, OutlineColor, RoundedCornerShape(10.dp))
                                                            .padding(4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        listOf("Student", "Library Staff").forEach { r ->
                                                            val isSelected = regRole == r
                                                            val label = if (r == "Student") "Murid" else "Staf"
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isSelected) ActiveLoansBg else Color.Transparent)
                                                                    .clickable { regRole = r }
                                                                    .padding(vertical = 10.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    label,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) DarkPurple else TextGray
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Janaan ID Pengguna", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .background(InputFieldBg, RoundedCornerShape(10.dp))
                                                            .padding(horizontal = 12.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Text(
                                                            text = regCustomId,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = PrimaryPurple,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // 4. Secure Password Generator Module
                                            Column {
                                                Text("Kata Laluan & Pengjana Rawak", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = regPassword_,
                                                        onValueChange = { regPassword_ = it },
                                                        placeholder = { Text("Klik jana rahsia...", fontSize = 13.sp) },
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )

                                                    Button(
                                                        onClick = {
                                                            regPassword_ = repository.generateSecurePassword()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = LightPurple, contentColor = DarkPurple),
                                                        border = BorderStroke(1.dp, OutlineColor),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                                        modifier = Modifier.height(52.dp)
                                                    ) {
                                                        Text("JANA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(
                                                    text = "* Secure password auto-generated for new staff/student.",
                                                    fontSize = 10.sp,
                                                    color = TextGray,
                                                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                                                )
                                            }

                                            // Feedback Message
                                            regSuccessMsg?.let { msg ->
                                                Text(msg, color = Color(0xFF006A6A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            regErrorMsg?.let { msg ->
                                                Text(msg, color = FinesText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Submit Registration
                                            Button(
                                                onClick = {
                                                    val res = repository.registerNewUser(regName, regEmail, regRole, regCustomId, regPassword_)
                                                    if (res.isSuccess) {
                                                        regSuccessMsg = "✅ Berjaya daftar! ID: $regCustomId"
                                                        regErrorMsg = null
                                                        // Reset form inputs except role
                                                        regName = ""
                                                        regEmail = ""
                                                        regPassword_ = ""
                                                    } else {
                                                        regErrorMsg = "❌ Ralat: ${res.exceptionOrNull()?.message}"
                                                        regSuccessMsg = null
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("DAFTAR AHLI BARU", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // List of registered users for review
                                item {
                                    Text("Senarai Ahli Sedia Ada (${users.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                                }

                                items(users) { usr ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, OutlineColor),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(usr.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                                Text("Emel: ${usr.email}", fontSize = 11.sp, color = TextGray)
                                                Text("ID: ${usr.id} • Kata Laluan: ${usr.passwordSecret}", fontSize = 11.sp, color = TextGray, fontFamily = FontFamily.Monospace)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (usr.role == "Admin" || usr.role == "Library Staff") LightPurple else ActiveLoansBg)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(usr.role, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkPurple)
                                            }
                                        }
                                    }
                                }

                            } else {
                                // TEACHER OR STUDENT ACCOUNT -> Sejarah Pinjaman (History log)
                                item {
                                    Text(
                                        text = "Sejarah Penuh Pinjaman Buku Anda",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }

                                val myHistoryLoans = loans.filter { it.userId.lowercase() == currentUserId.lowercase() }
                                if (myHistoryLoans.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White)
                                                .border(1.dp, OutlineColor, RoundedCornerShape(16.dp))
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Tiada rekod sejarah pinjaman lagi.", fontSize = 13.sp, color = TextGray)
                                        }
                                    }
                                } else {
                                    items(myHistoryLoans) { historyItem ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, OutlineColor),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        historyItem.bookTitle,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextDark,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    
                                                    val isReturned = historyItem.returnDateString != null
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isReturned) Color(0xFFD4EDDA) else FinesBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isReturned) "Dipulangkan" else "Dalam Pinjaman",
                                                            color = if (isReturned) Color(0xFF155724) else Color(0xFF721C24),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("ID Buku: ${historyItem.bookId}", fontSize = 11.sp, color = TextGray)
                                                Text("Tarikh Keluar: ${historyItem.borrowDateString}", fontSize = 11.sp, color = TextGray)
                                                
                                                historyItem.returnDateString?.let { retDate ->
                                                    Text("Tarikh Pulang: $retDate", fontSize = 11.sp, color = TextGray)
                                                }

                                                if (historyItem.fineAmount > 0.0) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Denda Kelewatan:",
                                                            fontSize = 11.sp,
                                                            color = FinesText
                                                        )
                                                        Text(
                                                            text = "RM ${String.format("%.2f", historyItem.fineAmount)} (${if (historyItem.finePaid) "Telah Dibayar" else "Belum Dibayar"})",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (historyItem.finePaid) SuccessGreen else FinesText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(20.dp)) }
                        }
                    }

                    3 -> {
                        // --- NAVIGATION ITEM 3: SYSTEM CONFIGURE (Admin adds books or App information) ---
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (currentUserRole == "Admin") {
                                item {
                                    Text(
                                        text = "Urus Buku & Tetapan Sistem",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }

                                // 1. Add Book Form
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Tambah Buku Baru",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = PrimaryPurple
                                            )

                                            OutlinedTextField(
                                                value = addBookTitle,
                                                onValueChange = { addBookTitle = it },
                                                label = { Text("Tajuk Buku", fontSize = 12.sp) },
                                                placeholder = { Text("Masukkan tajuk buku penuh") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )

                                            OutlinedTextField(
                                                value = addBookAuthor,
                                                onValueChange = { addBookAuthor = it },
                                                label = { Text("Nama Penulis", fontSize = 12.sp) },
                                                placeholder = { Text("Masukkan penulis") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )

                                            OutlinedTextField(
                                                value = addBookIsbn,
                                                onValueChange = { addBookIsbn = it },
                                                label = { Text("Koda ISBN", fontSize = 12.sp) },
                                                placeholder = { Text("cth: 9780140439000") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1.5f)) {
                                                    Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState())
                                                            .border(1.dp, OutlineColor, RoundedCornerShape(10.dp))
                                                            .padding(4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        listOf("Fiction", "Drama", "History", "Science & Technology", "Literature").forEach { catOption ->
                                                            val isOptionSelected = addBookCategory == catOption
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(if (isOptionSelected) ActiveLoansBg else Color.Transparent)
                                                                    .clickable { addBookCategory = catOption }
                                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    catOption,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isOptionSelected) DarkPurple else TextGray
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(0.7f)) {
                                                    Text("Kuantiti", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    OutlinedTextField(
                                                        value = addBookQty,
                                                        onValueChange = { addBookQty = it },
                                                        placeholder = { Text("cth: 3") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                }
                                            }

                                            // Status message
                                            addSuccessMsg?.let { msg ->
                                                Text(msg, color = Color(0xFF006A6A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            addErrorMsg?.let { msg ->
                                                Text(msg, color = FinesText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    val qtyInt = addBookQty.toIntOrNull() ?: 0
                                                    val res = repository.addBook(addBookTitle, addBookAuthor, addBookIsbn, addBookCategory, qtyInt)
                                                    if (res.isSuccess) {
                                                        addSuccessMsg = "✅ Buku '${addBookTitle}' berjaya dimasukkan!"
                                                        addErrorMsg = null
                                                        addBookTitle = ""
                                                        addBookAuthor = ""
                                                        addBookIsbn = ""
                                                        addBookQty = "3"
                                                    } else {
                                                        addErrorMsg = "❌ Ralat: ${res.exceptionOrNull()?.message}"
                                                        addSuccessMsg = null
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("DAFTAR BUKU BARU", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // 2. Fine Rate Configuration Form
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Text(
                                                text = "Konfigurasi Kadar Denda Kelewatan",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = PrimaryPurple
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Kadar Semasa: RM " + String.format("%.2f", fineRate) + " sehari",
                                                fontSize = 12.sp,
                                                color = TextGray
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = editFineTextField,
                                                onValueChange = { editFineTextField = it },
                                                label = { Text("Kadar Baru Sehari (RM)", fontSize = 12.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Button(
                                                onClick = {
                                                    val rVal = editFineTextField.toDoubleOrNull()
                                                    if (rVal != null && rVal >= 0.0) {
                                                        repository.updateFineRate(rVal)
                                                        Toast.makeText(context, "Kadar denda dikemaskini!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Kadar tidak sah!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("KEMASKINI DENDA", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                            } else {
                                // TEACHER OR STUDENT: Info view
                                item {
                                    Text(
                                        text = "Hubungi Pusat Sumber SMK St. Mary",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, OutlineColor)
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Text("📍 Lokasi Perpustakaan:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkPurple)
                                            Text("Tingkat 2, Blok B, Pusat Sumber SMK St. Mary.", fontSize = 13.sp, color = TextGray)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text("⏰ Waktu Operasi Kaunter:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkPurple)
                                            Text("• Isnin - Khamis: 7:30 Pagi — 4:30 Petang", fontSize = 13.sp, color = TextGray)
                                            Text("• Jumaat: 7:30 Pagi — 12:30 Tengah Hari", fontSize = 13.sp, color = TextGray)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text("📞 Maklumat Perhubungan:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkPurple)
                                            Text("E-mel: pusat_sumber@stmary.edu.my", fontSize = 13.sp, color = TextGray)
                                            Text("Telefon: 03-6258 5555", fontSize = 13.sp, color = TextGray)
                                        }
                                    }
                                }
                            }

                            // Universal developer resets (extremely good craft & handy in staging/test runs)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
                                    border = BorderStroke(1.dp, Color(0xFFFFCCCC)),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "⚠️ Zon Penterjemah Pembangun (Zon Hijau)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF990000)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Setkan semula semua data katalog, pinjaman, denda, dan mock-user kembali ke tetapan asal kilang SMK St. Mary untuk ujian bersih.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF660000)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                repository.resetToDefaults()
                                                Toast.makeText(context, "Sistem berjaya ditetapkan semula ke konfigurasi asal!", Toast.LENGTH_SHORT).show()
                                                handleLogout()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("SET SEMULA DEMO DATABASE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(20.dp)) }
                        }
                    }
                }
            }

            // Custom navigation selector, matching bottom navigation layout of Sleek Interface
            CustomBottomNav(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                currentUserRole = currentUserRole
            )
        }
    }
}

@Composable
fun CustomBottomNav(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    currentUserRole: String
) {
    Surface(
        color = Color(0xFFF3EDF7),
        border = BorderStroke(width = 0.5.dp, color = CardBorderColor),
        modifier = Modifier.navigationBarsPadding() // Safely handle gesture navigators at bottom edge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab index 0: Overview / Work Desk
            NavItemBlock(
                isSelected = activeTab == 0,
                iconSelected = Icons.Default.GridView,
                iconUnselected = Icons.Default.GridView,
                label = if (currentUserRole == "Admin" || currentUserRole == "Library Staff") "Kaunter Desk" else "Ringkasan",
                onClick = { onTabSelected(0) }
            )

            // Tab index 1: Catalog Search
            NavItemBlock(
                isSelected = activeTab == 1,
                iconSelected = Icons.Default.Book,
                iconUnselected = Icons.Default.Book,
                label = "Katalog",
                onClick = { onTabSelected(1) }
            )

            // Tab index 2: Admin Registration -or- Personal Loan History
            NavItemBlock(
                isSelected = activeTab == 2,
                iconSelected = Icons.Default.People,
                iconUnselected = Icons.Default.People,
                label = if (currentUserRole == "Admin") "Daftar Ahli" else "Rekod Saya",
                onClick = { onTabSelected(2) }
            )

            // Tab index 3: Config / Contact
            NavItemBlock(
                isSelected = activeTab == 3,
                iconSelected = Icons.Default.Settings,
                iconUnselected = Icons.Default.Settings,
                label = if (currentUserRole == "Admin") "Sistem" else "Maklumat",
                onClick = { onTabSelected(3) }
            )
        }
    }
}

@Composable
fun RowScope.NavItemBlock(
    isSelected: Boolean,
    iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    iconUnselected: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) ActiveLoansBg else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) iconSelected else iconUnselected,
                contentDescription = label,
                tint = if (isSelected) DarkPurple else TextDark,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) DarkPurple else TextGray
        )
    }
}
