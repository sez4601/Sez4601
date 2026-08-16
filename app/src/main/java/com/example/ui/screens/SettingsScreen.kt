package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProductViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SettingsScreenContent(
    viewModel: ProductViewModel,
    products: List<ProductEntity>,
    onNavigateToProducts: () -> Unit,
    onNavigateToAddProduct: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val isSoundEnabled by viewModel.alertSoundEnabled.collectAsState()
    val isVibrationEnabled by viewModel.alertVibrationEnabled.collectAsState()

    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // JSON Yükleme Dosya Seçici
    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    viewModel.importJsonBackup(stringBuilder.toString())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Dosya okunamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // JSON Yedeği Dışa Aktarma Paylaşımı
    fun exportBackup() {
        coroutineScope.launch {
            val json = viewModel.exportJsonBackup()
            if (json.isBlank() || json == "[]") {
                Toast.makeText(context, "Yedeklenecek ürün bulunmuyor.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Panoya kopyala ve paylaşım başlat
            clipboardManager.setText(AnnotatedString(json))
            try {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_TITLE, "SKT_Takip_Yedek.json")
                    type = "application/json"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Yedek Dosyasını Kaydet / Paylaş")
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Yedek JSON kopyalandı!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // KART 1: 🧪 Uyarıyı Test Et
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B5C)),
            border = BorderStroke(1.5.dp, Color(0xFF2575FC).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Uyarıyı Test Et",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                // Turuncu/Kırmızı "Seçili Modla Test Et" Butonu
                Button(
                    onClick = { viewModel.testAlert() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722) // Canlı Turuncu-Kırmızı
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Seçili Modla Test Et",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Ses & Titreşim Mod Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Ses Butonu
                    Surface(
                        onClick = { viewModel.toggleAlertSound() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSoundEnabled) Color(0xFF1E40AF) else Color(0xFF0A1D40),
                        border = BorderStroke(
                            1.5.dp,
                            if (isSoundEnabled) Color(0xFF3B82F6) else Color(0x33FFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Ses",
                                tint = if (isSoundEnabled) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ses",
                                color = if (isSoundEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Titreşim Butonu
                    Surface(
                        onClick = { viewModel.toggleAlertVibration() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isVibrationEnabled) Color(0xFF1E40AF) else Color(0xFF0A1D40),
                        border = BorderStroke(
                            1.5.dp,
                            if (isVibrationEnabled) Color(0xFF3B82F6) else Color(0x33FFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isVibrationEnabled) Icons.Default.Vibration else Icons.Default.Smartphone,
                                contentDescription = "Titreşim",
                                tint = if (isVibrationEnabled) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Titreşim",
                                color = if (isVibrationEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // KART 2: 📦 Veri Yedekleme
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B5C)),
            border = BorderStroke(1.5.dp, Color(0xFF2575FC).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Veri Yedekleme",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                // Bilgilendirme Kutusu (Yeşil/Teal kutu)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF063E48),
                    border = BorderStroke(1.dp, Color(0xFF0D9488))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "💡", fontSize = 14.sp)
                            Text(
                                text = "Verilerinizi güvende tutun!",
                                color = Color(0xFF5EEAD4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Yedek dosyası indirerek verilerinizi bilgisayarınıza veya bulut depolamanıza kaydedin. Telefon değişse bile verilerinizi geri yükleyebilirsiniz.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 2 Büyük Aksiyon Kartı (Yedeği İndir / Yedeği Yükle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sol: Yedeği İndir (Koyu Mavi)
                    Surface(
                        onClick = { exportBackup() },
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0A1D40),
                        border = BorderStroke(1.5.dp, Color(0xFF335C9E))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Yedeği İndir",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${products.size} ürün",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Sağ: Yedeği Yükle (Parlak Mavi Dolgulu)
                    Surface(
                        onClick = { jsonFilePicker.launch("application/json") },
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0084FF),
                        border = BorderStroke(1.5.dp, Color(0xFF60A5FA))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFFFFD600), // Sarı Klasör
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Yedeği Yükle",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "JSON dosyası",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // KART 3: 💾 Veri Durumu
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B5C)),
            border = BorderStroke(1.5.dp, Color(0xFF2575FC).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Veri Durumu",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                // Satır 1: Kayıtlı ürün
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kayıtlı ürün:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${products.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }

                // Satır 2: Depolama
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Depolama:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "asyncStorage",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                // Satır 3: Durum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Durum:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )
                        Text(
                            text = "Çevrimdışı Hazır",
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Yeşil Kutucuk: Verileriniz Güvende
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF063B34),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "✅ Verileriniz Güvende",
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• İnternet olmadan çalışır",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "• Telefon kapansa bile veriler kalır",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "• Siz silmeden veriler silinmez",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "• Yedek alarak ekstra güvenlik sağlayın",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Koyu Kırmızı / Bordo "Tüm Ürünleri Sil" Butonu
                Surface(
                    onClick = { showDeleteAllConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3B1828),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tüm Ürünleri Sil",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // FOOTER / ALT METİN
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "SKT Takip v1.1",
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "Market çalışanları için SKT kontrol sistemi",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
            Text(
                text = "🔒 Çevrimdışı • 💾 Kalıcı Depolama • 📱 Mobil Uyumlu",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
    }

    // Tüm Ürünleri Silme Onay Diyaloğu
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Tüm Ürünleri Sil?", fontWeight = FontWeight.Bold) },
            text = { Text("Tüm kayıtlı ürünler kalıcı olarak silinecektir. Bu işlem geri alınamaz. Onaylıyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showDeleteAllConfirm = false
                        Toast.makeText(context, "Tüm ürünler silindi", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Evet, Hepsini Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
