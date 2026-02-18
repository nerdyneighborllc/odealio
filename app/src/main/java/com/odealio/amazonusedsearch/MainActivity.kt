package com.odealio.amazonusedsearch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

data class UsedListing(
    val title: String,
    val price: String,
    val shipping: String,
    val url: String
)

class AmazonSearchViewModel : ViewModel() {
    var listings by mutableStateOf<List<UsedListing>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val client = OkHttpClient.Builder().build()

    fun search(query: String) {
        if (query.isBlank()) {
            listings = emptyList()
            errorMessage = "Enter something to search."
            return
        }

        viewModelScope.launch {
            loading = true
            errorMessage = null

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fetchUsedListings(query)
                }
            }

            result.onSuccess {
                listings = it
                if (it.isEmpty()) {
                    errorMessage = "No used listings found for \"$query\"."
                }
            }.onFailure {
                listings = emptyList()
                errorMessage = "Search failed: ${it.message ?: "unknown error"}"
            }

            loading = false
        }
    }

    private fun fetchUsedListings(query: String): List<UsedListing> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://www.amazon.com/s?k=$encodedQuery&rh=p_n_condition-type%3A6461716011"

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val html = response.body?.string().orEmpty()
            val document = Jsoup.parse(html)

            return document.select("div.s-result-item[data-component-type=s-search-result]")
                .mapNotNull { card ->
                    val title = card.selectFirst("h2 span")?.text()?.trim().orEmpty()
                    val price = card.selectFirst("span.a-price span.a-offscreen")?.text()?.trim().orEmpty()
                    val shipping = card.selectFirst("span.a-color-base")
                        ?.text()
                        ?.takeIf { it.contains("shipping", ignoreCase = true) }
                        ?.trim()
                        ?: "Shipping not shown"
                    val href = card.selectFirst("h2 a")?.attr("href").orEmpty()

                    if (title.isBlank() || price.isBlank() || href.isBlank()) {
                        null
                    } else {
                        val fullUrl = if (href.startsWith("http")) href else "https://www.amazon.com$href"
                        UsedListing(title = title, price = price, shipping = shipping, url = fullUrl)
                    }
                }
                .take(20)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AmazonUsedPriceSearchScreen()
            }
        }
    }
}

@Composable
private fun AmazonUsedPriceSearchScreen(viewModel: AmazonSearchViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Amazon Used Price Search",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Search Amazon used listings and tap a result to open it in your browser.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                label = { Text("Search term") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { viewModel.search(query) }) {
                        Text("Search")
                    }
                }
            )

            if (viewModel.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            viewModel.errorMessage?.let { error ->
                AssistChip(onClick = {}, label = { Text(error) })
            }

            ListingsList(listings = viewModel.listings)
        }
    }
}

@Composable
private fun ListingsList(listings: List<UsedListing>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(items = listings, key = { it.url.lowercase(Locale.US) }) { listing ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(listing.url))
                        context.startActivity(intent)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = listing.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = listing.price, style = MaterialTheme.typography.titleLarge)
                    Text(text = listing.shipping, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (listings.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("No results yet")
                }
            }
        }
    }
}
