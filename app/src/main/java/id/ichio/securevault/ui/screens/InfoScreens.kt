@file:OptIn(ExperimentalMaterial3Api::class)

package id.ichio.securevault.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import id.ichio.securevault.ui.theme.AppGradient
import id.ichio.securevault.ui.theme.Dot
import id.ichio.securevault.ui.theme.SectionCard
import id.ichio.securevault.ui.theme.accentFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.ichio.securevault.ui.theme.GradientHeader

const val APP_AUTHOR = "Ichio"
private const val FALLBACK_VERSION = "1.0"

private data class HelpTopic(val title: String, val body: String)

private val helpTopics = listOf(
    HelpTopic(
        "1. Memulai & PIN",
        "• Saat pertama kali dibuka, buat PIN 4–12 digit lalu ulangi untuk konfirmasi.\n" +
            "• Jika perangkat mendukung, Anda akan ditawari membuka aplikasi dengan sidik jari/biometrik.\n" +
            "• Setiap kali aplikasi dibuka, masukkan PIN atau gunakan biometrik."
    ),
    HelpTopic(
        "2. Menyimpan akun & password",
        "• Buka tab Akun, tekan tombol + di kanan bawah.\n" +
            "• Isi Nama akun/layanan (wajib), Username/Email, Password, URL dan Catatan (opsional).\n" +
            "• Tekan ikon ⟳ di kolom password untuk membuat password acak yang kuat.\n" +
            "• Tekan ✓ di kanan atas atau tombol Simpan."
    ),
    HelpTopic(
        "3. Melihat, menyalin & mengedit",
        "• Di daftar akun, tekan ikon orang untuk menyalin username dan ikon kunci untuk menyalin password.\n" +
            "• Tekan sebuah akun untuk membuka detailnya; ikon mata menampilkan/menyembunyikan password.\n" +
            "• Password yang disalin otomatis dihapus dari clipboard setelah 30 detik.\n" +
            "• Untuk menghapus akun, buka detailnya lalu tekan ikon tempat sampah.\n" +
            "• Gunakan kolom pencarian untuk mencari berdasarkan nama, username atau URL."
    ),
    HelpTopic(
        "4. Generator password",
        "• Buka tab Generator.\n" +
            "• Atur panjang (6–64 karakter) dan jenis karakter: huruf besar, huruf kecil, angka, simbol.\n" +
            "• Aktifkan \"Hindari karakter mirip\" agar tidak ada I, l, 1, O, 0 yang membingungkan.\n" +
            "• Tekan Generate untuk membuat ulang, lalu Salin. Indikator menunjukkan kekuatan password.\n" +
            "• Rekomendasi: minimal 16 karakter dengan semua jenis karakter aktif."
    ),
    HelpTopic(
        "5. Catatan",
        "• Buka tab Catatan, tekan + untuk membuat catatan baru.\n" +
            "• Isi judul dan isi catatan, lalu tekan ✓. Isi catatan disimpan terenkripsi.\n" +
            "• Tekan catatan untuk mengedit, ikon tempat sampah untuk menghapus."
    ),
    HelpTopic(
        "6. Backup data",
        "• Buka Pengaturan → Backup data.\n" +
            "• Buat password backup (minimal 8 karakter) dan ulangi.\n" +
            "• Pilih lokasi penyimpanan file .svbk (memori HP, Google Drive, dll).\n" +
            "• Backup berisi SEMUA akun, password dan catatan dalam bentuk terenkripsi.\n" +
            "• PENTING: catat password backup di tempat aman. Tanpa password ini backup tidak bisa dibuka.\n" +
            "• Lakukan backup secara rutin dan simpan salinannya di luar HP."
    ),
    HelpTopic(
        "7. Restore data",
        "• Buka Pengaturan → Restore data, lalu pilih file .svbk.\n" +
            "• Masukkan password backup.\n" +
            "• Pilih mode:\n" +
            "   – Gabungkan: data backup ditambahkan, entri yang sama persis dilewati.\n" +
            "   – Ganti semua: data saat ini dihapus dan diganti isi backup.\n" +
            "• Restore bisa dilakukan di HP lain (misalnya saat ganti HP)."
    ),
    HelpTopic(
        "8. Keamanan & kunci otomatis",
        "• Aplikasi terkunci otomatis jika ditinggal di background lebih dari 30 detik.\n" +
            "• Tekan Pengaturan → Kunci sekarang untuk mengunci manual.\n" +
            "• Salah PIN 5 kali akan mengunci aplikasi sementara (30 detik, makin lama jika terus salah).\n" +
            "• Screenshot dan rekam layar diblokir demi keamanan.\n" +
            "• Semua data hanya disimpan di HP Anda, tidak dikirim ke internet."
    ),
    HelpTopic(
        "9. Ganti PIN & biometrik",
        "• Pengaturan → Ganti PIN: masukkan PIN lama, lalu PIN baru dua kali.\n" +
            "• Pengaturan → Buka dengan biometrik: aktifkan/nonaktifkan sidik jari."
    ),
    HelpTopic(
        "10. Masa berlaku password & notifikasi",
        "• Di form akun, bagian \"Masa berlaku password\", pilih:\n" +
            "   – Ganti setiap 30/60/90/180/365 hari: tanggal kedaluwarsa dihitung dari hari ini dan otomatis " +
            "diperbarui setiap kali password diganti.\n" +
            "   – Pilih tanggal…: tentukan tanggal kedaluwarsa sendiri.\n" +
            "   – Tidak ada masa berlaku: password tidak pernah kedaluwarsa.\n" +
            "• Akun yang akan kedaluwarsa (≤ 2 hari) ditandai oranye, yang sudah kedaluwarsa ditandai merah.\n" +
            "• Di tab Akun, ketuk tombol \"… password perlu diganti\" untuk menampilkan akun-akun tersebut saja.\n" +
            "• Notifikasi otomatis muncul 2 hari sebelum password kedaluwarsa, dan sekali lagi saat sudah kedaluwarsa. " +
            "Pengecekan berjalan di latar belakang walau aplikasi ditutup.\n" +
            "• Di Android 13 ke atas, izinkan notifikasi saat diminta. Pengaturan → Notifikasi untuk mengaktifkan/menonaktifkan.\n" +
            "• Isi notifikasi hanya menampilkan nama akun, tidak pernah password."
    ),
    HelpTopic(
        "11. Tugas, subtask & pengingat",
        "• Buka tab Tugas, tekan \"Tugas baru\".\n" +
            "• Isi judul dan catatan (opsional).\n" +
            "• Tambahkan subtask satu per satu di bagian Subtask, lalu tekan + atau Enter.\n" +
            "• Aktifkan Pengingat untuk mendapat notifikasi: pilih cepat (1 jam lagi, Malam 19:00, Besok 08:00) " +
            "atau atur tanggal & jam sendiri.\n" +
            "• Centang lingkaran di daftar untuk menandai tugas atau subtask selesai. " +
            "Jika semua subtask dicentang, tugas otomatis selesai.\n" +
            "• Dari notifikasi, tekan \"Tandai selesai\" tanpa perlu membuka aplikasi.\n" +
            "• Ketuk kotak Aktif / Selesai di atas untuk berpindah tampilan.\n" +
            "• Pengingat tetap berjalan setelah HP dinyalakan ulang. Tugas ikut tersimpan di file backup."
    ),
    HelpTopic(
        "12. Cek kebocoran data (Have I Been Pwned)",
        "• Buka tab Generator, gulir ke bagian \"Cek kebocoran data\".\n" +
            "• Cek password: ketik password (atau tekan \"Pakai hasil generator\"), lalu Periksa. " +
            "Gratis dan aman — hanya 5 karakter awal hash SHA-1 yang dikirim, password asli tidak pernah keluar dari HP.\n" +
            "• Audit: tekan \"Periksa semua password tersimpan\" untuk mengetahui akun mana yang password-nya pernah bocor.\n" +
            "• Cek email: memerlukan API key berlangganan dari haveibeenpwned.com/API/Key. " +
            "Tekan ikon kunci untuk menyimpan API key (disimpan terenkripsi). Hasilnya menampilkan nama layanan yang bocor, " +
            "tanggal, dan jenis data yang bocor.\n" +
            "• Jika ditemukan kebocoran: segera ganti password di layanan tersebut, jangan pakai ulang password yang sama, " +
            "dan aktifkan verifikasi 2 langkah.\n" +
            "• Fitur ini satu-satunya yang memakai internet, dan hanya berjalan saat Anda menekan tombol Periksa."
    ),
    HelpTopic(
        "13. Pertanyaan umum (FAQ)",
        "• Lupa PIN aplikasi?\n" +
            "  PIN tidak bisa dipulihkan. Hapus data aplikasi (Setelan HP → Aplikasi → SecureVault → Penyimpanan → Hapus data), " +
            "buat PIN baru, lalu restore dari file backup.\n\n" +
            "• Lupa password backup?\n" +
            "  File backup tersebut tidak dapat dibuka. Buat backup baru dengan password yang Anda ingat.\n\n" +
            "• Ganti HP / reset pabrik?\n" +
            "  Lakukan Backup di HP lama, pindahkan file .svbk, install SecureVault di HP baru, lalu Restore.\n\n" +
            "• Kenapa layar hitam saat screenshot?\n" +
            "  Itu disengaja untuk melindungi password Anda dari tangkapan layar."
    )
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            GradientHeader(
                title = "Bantuan",
                subtitle = "Panduan pemakaian SecureVault",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Panduan pemakaian SecureVault. Tekan topik untuk membuka penjelasannya.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(helpTopics, key = { it.title }) { topic -> HelpCard(topic) }
        }
    }
}

@Composable
private fun HelpCard(topic: HelpTopic) {
    var expanded by rememberSaveable(topic.title) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(accentFor(topic.title), size = 10.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Tutup" else "Buka"
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(topic.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: FALLBACK_VERSION
        } catch (e: Exception) {
            FALLBACK_VERSION
        }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = "Tentang",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(AppGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("SecureVault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Versi $version",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pengelola password, generator password dan catatan terenkripsi.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            SectionCard {
                InfoRow("Dibuat oleh", APP_AUTHOR)
                HorizontalDivider()
                InfoRow("Versi", version)
                HorizontalDivider()
                InfoRow("Platform", "Android 8.0 ke atas")
                HorizontalDivider()
                InfoRow("Enkripsi", "AES-256-GCM (Android Keystore)")
                HorizontalDivider()
                InfoRow("Backup", "PBKDF2-SHA256 + AES-256-GCM")
                HorizontalDivider()
                InfoRow("Internet", "Hanya cek kebocoran")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "© 2026 $APP_AUTHOR. Semua data disimpan hanya di perangkat Anda.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
    )
}
