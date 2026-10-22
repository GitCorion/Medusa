package tv.medusa.app.data.demo

import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.EpisodeItem
import tv.medusa.app.domain.model.SeasonItem
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase

/**
 * Gestor del Catálogo de Demostración Local para el proceso de revisión de Google Play Store.
 *
 * Permite a los revisores de Google y usuarios en pruebas explorar la interfaz completa de Medusa,
 * navegar por categorías y géneros, probar la reproducción continua con ExoPlayer (usando streams
 * de dominio público de Google Cloud Storage) y validar el diálogo de compra de Medusa Pro
 * sin depender de un servidor Jellyfin personal externo encendido.
 */
object DemoCatalogManager {

    const val DEMO_SERVER_URL = "https://demo.medusa.tv"
    const val DEMO_USER_ID = "demo_reviewer_user"
    const val DEMO_TOKEN = "demo_reviewer_token"
    const val DEMO_USERNAME = "PlayStore Reviewer"

    // Streams públicos de alta disponibilidad alojados en Google Cloud Storage CDN
    const val STREAM_BIG_BUCK_BUNNY = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    const val STREAM_SINTEL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    const val STREAM_TEARS_OF_STEEL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
    const val STREAM_ELEPHANTS_DREAM = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    const val STREAM_COSMOS_LAUNDROMAT = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"
    const val STREAM_NIGHT_OF_LIVING_DEAD = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    const val STREAM_SERIES_EP1 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    const val STREAM_SERIES_EP2 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
    const val STREAM_SERIES_EP3 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4"

    fun isDemoServer(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.trim().trimEnd('/').lowercase()
        return clean == DEMO_SERVER_URL.lowercase() ||
                clean == "demo" ||
                clean == "https://demo" ||
                clean.contains("demo.medusa.tv") ||
                clean.contains("demo.jellyfin.org")
    }

    fun isDemoReviewerUser(username: String?): Boolean {
        if (username.isNullOrBlank()) return false
        val clean = username.trim().lowercase()
        return clean in listOf(
            "playstore_reviewer",
            "playstore",
            "reviewer",
            "demo",
            "google",
            "google_reviewer",
            "googleplay"
        )
    }

    fun isDemoMediaId(id: String): Boolean = id.startsWith("demo_")

    fun getDemoStreamUrl(id: String): String = when (id) {
        "demo_big_buck_bunny" -> STREAM_BIG_BUCK_BUNNY
        "demo_sintel" -> STREAM_SINTEL
        "demo_tears_of_steel" -> STREAM_TEARS_OF_STEEL
        "demo_elephants_dream" -> STREAM_ELEPHANTS_DREAM
        "demo_cosmos_laundromat" -> STREAM_COSMOS_LAUNDROMAT
        "demo_night_of_living_dead" -> STREAM_NIGHT_OF_LIVING_DEAD
        "demo_series_blender", "demo_ep_1" -> STREAM_SERIES_EP1
        "demo_ep_2" -> STREAM_SERIES_EP2
        "demo_ep_3" -> STREAM_SERIES_EP3
        else -> STREAM_BIG_BUCK_BUNNY
    }

    fun getDemoSeasons(seriesId: String): List<SeasonItem> {
        return listOf(
            SeasonItem(
                id = "demo_season_1",
                name = "Temporada 1",
                seasonNumber = 1,
                episodeCount = 3
            )
        )
    }

    fun getDemoEpisodes(seriesId: String, seasonId: String = ""): List<EpisodeItem> {
        return listOf(
            EpisodeItem(
                id = "demo_ep_1",
                name = "La Señal",
                episodeNumber = 1,
                seasonNumber = 1,
                overview = "Una expedición de reconocimiento detecta una misteriosa transmisión procedente de una luna helada en los confines del sistema solar.",
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/640px-Tears_of_Steel_-_Hero_Shot_02.png",
                durationMinutes = 15
            ),
            EpisodeItem(
                id = "demo_ep_2",
                name = "Punto de No Retorno",
                episodeNumber = 2,
                seasonNumber = 1,
                overview = "El equipo de avanzada debe descender a las profundidades de la grieta antes de que la tormenta de radiación solar corte las comunicaciones con la base.",
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Tears_of_Steel_poster.jpg/640px-Tears_of_Steel_poster.jpg",
                durationMinutes = 18
            ),
            EpisodeItem(
                id = "demo_ep_3",
                name = "Horizontes Lejanos",
                episodeNumber = 3,
                seasonNumber = 1,
                overview = "Un descubrimiento inesperado en el núcleo de la estación cambia para siempre la comprensión humana sobre los viajes hiperespaciales.",
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Elephants_Dream_Proog_and_Emo.jpg/640px-Elephants_Dream_Proog_and_Emo.jpg",
                durationMinutes = 22
            )
        )
    }

    fun getDemoEntities(): List<JellyfinMediaEntity> {
        return listOf(
            JellyfinMediaEntity(
                id = "demo_big_buck_bunny",
                title = "Big Buck Bunny",
                originalTitle = "Big Buck Bunny",
                type = "Movie",
                communityRating = 7.9f,
                productionYear = 2008,
                genres = "Animación, Comedia, Familiar",
                officialRating = "ATP",
                overview = "Un conejo gigante y de gran corazón vive apaciblemente en el bosque hasta que tres pequeños y traviesos roedores comienzan a burlarse de él y de sus amigos. Decidido a poner orden, prepara una ingeniosa venganza.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/640px-Big_buck_bunny_poster_big.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Big_Buck_Bunny_-_First_render.png/1280px-Big_Buck_Bunny_-_First_render.png",
                playbackPositionTicks = 250_000_000L, // 25 segundos para alimentar "Continuar Viendo"
                lastPlayedTicks = System.currentTimeMillis() * 10_000L
            ),
            JellyfinMediaEntity(
                id = "demo_sintel",
                title = "Sintel",
                originalTitle = "Sintel",
                type = "Movie",
                communityRating = 8.2f,
                productionYear = 2010,
                genres = "Animación, Fantasía, Aventura",
                officialRating = "+12",
                overview = "Sintel es una joven solitaria que rescata y cuida a un pequeño dragón herido al que llama Scales. Cuando un dragón enorme se lo arrebata, emprende un épico viaje a través de tierras desérticas y picos helados para recuperarlo.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Sintel_poster.jpg/640px-Sintel_poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/Sintel_Cover_Art.png/1280px-Sintel_Cover_Art.png",
                playbackPositionTicks = 0L,
                isFavorite = true
            ),
            JellyfinMediaEntity(
                id = "demo_tears_of_steel",
                title = "Tears of Steel",
                originalTitle = "Tears of Steel",
                type = "Movie",
                communityRating = 7.6f,
                productionYear = 2012,
                genres = "Ciencia ficción, Acción",
                officialRating = "+13",
                overview = "Ambientada en un futuro postapocalíptico distópico en Ámsterdam, un grupo de científicos y combatientes intentan desesperadamente salvar la Tierra de una invasión de robots gigantes impulsados por tecnología cibernética.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Tears_of_Steel_poster.jpg/640px-Tears_of_Steel_poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/1280px-Tears_of_Steel_-_Hero_Shot_02.png",
                playbackPositionTicks = 0L
            ),
            JellyfinMediaEntity(
                id = "demo_elephants_dream",
                title = "Elephants Dream",
                originalTitle = "Elephants Dream",
                type = "Movie",
                communityRating = 7.3f,
                productionYear = 2006,
                genres = "Animación, Ciencia ficción, Misterio",
                officialRating = "+12",
                overview = "Dos personajes, el sabio y anciano Proog y su joven aprendiz Emo, exploran el interior de una máquina gigantesca y misteriosa capaz de transformar la realidad a su alrededor según sus propios pensamientos.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Elephants_Dream_poster.jpg/640px-Elephants_Dream_poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Elephants_Dream_Proog_and_Emo.jpg/1280px-Elephants_Dream_Proog_and_Emo.jpg",
                playbackPositionTicks = 0L
            ),
            JellyfinMediaEntity(
                id = "demo_cosmos_laundromat",
                title = "Cosmos Laundromat",
                originalTitle = "Cosmos Laundromat",
                type = "Movie",
                communityRating = 8.0f,
                productionYear = 2015,
                genres = "Animación, Fantasía, Drama",
                officialRating = "+16",
                overview = "En una isla desolada, una oveja melancólica llamada Franck está a punto de rendirse cuando conoce a Victor, un excéntrico vendedor que le ofrece una segunda oportunidad en una lavandería cósmica que conecta infinitas vidas posibles.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Cosmos_Laundromat_Poster.jpg/640px-Cosmos_Laundromat_Poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/Cosmos_Laundromat_First_Cycle_Poster.jpg/1280px-Cosmos_Laundromat_First_Cycle_Poster.jpg",
                playbackPositionTicks = 0L
            ),
            JellyfinMediaEntity(
                id = "demo_night_of_living_dead",
                title = "La Noche de los Muertos Vivientes",
                originalTitle = "Night of the Living Dead",
                type = "Movie",
                communityRating = 8.4f,
                productionYear = 1968,
                genres = "Terror, Misterio, Clásico",
                officialRating = "+16",
                overview = "La icónica obra maestra del cine de terror de George A. Romero. Un grupo dispar de personas se atrinchera en una granja abandonada para protegerse de una repentina horda de muertos vivientes que asola el país.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/17/Night_of_the_Living_Dead_1968.jpg/640px-Night_of_the_Living_Dead_1968.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Night_of_the_Living_Dead_still.jpg/1280px-Night_of_the_Living_Dead_still.jpg",
                playbackPositionTicks = 0L
            ),
            // Serie de demostración
            JellyfinMediaEntity(
                id = "demo_series_blender",
                title = "Crónicas del Mañana",
                originalTitle = "Chronicles of Tomorrow",
                type = "Series",
                communityRating = 8.1f,
                productionYear = 2023,
                genres = "Ciencia ficción, Aventura",
                officialRating = "+13",
                overview = "Una miniserie antológica que explora las fronteras del espacio profundo, las nuevas tecnologías y la supervivencia de la humanidad en mundos inexplorados.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Tears_of_Steel_poster.jpg/640px-Tears_of_Steel_poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/1280px-Tears_of_Steel_-_Hero_Shot_02.png",
                totalItemCount = 3,
                unplayedItemCount = 3
            ),
            // Episodios de la serie
            JellyfinMediaEntity(
                id = "demo_ep_1",
                title = "La Señal",
                type = "Episode",
                seriesId = "demo_series_blender",
                seriesName = "Crónicas del Mañana",
                seasonNumber = 1,
                episodeNumber = 1,
                productionYear = 2023,
                overview = "Una expedición de reconocimiento detecta una misteriosa transmisión procedente de una luna helada en los confines del sistema solar.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/640px-Tears_of_Steel_-_Hero_Shot_02.png",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/1280px-Tears_of_Steel_-_Hero_Shot_02.png",
                playbackPositionTicks = 0L
            ),
            JellyfinMediaEntity(
                id = "demo_ep_2",
                title = "Punto de No Retorno",
                type = "Episode",
                seriesId = "demo_series_blender",
                seriesName = "Crónicas del Mañana",
                seasonNumber = 1,
                episodeNumber = 2,
                productionYear = 2023,
                overview = "El equipo de avanzada debe descender a las profundidades de la grieta antes de que la tormenta de radiación solar corte las comunicaciones con la base.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Tears_of_Steel_poster.jpg/640px-Tears_of_Steel_poster.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Tears_of_Steel_-_Hero_Shot_02.png/1280px-Tears_of_Steel_-_Hero_Shot_02.png",
                playbackPositionTicks = 0L
            ),
            JellyfinMediaEntity(
                id = "demo_ep_3",
                title = "Horizontes Lejanos",
                type = "Episode",
                seriesId = "demo_series_blender",
                seriesName = "Crónicas del Mañana",
                seasonNumber = 1,
                episodeNumber = 3,
                productionYear = 2023,
                overview = "Un descubrimiento inesperado en el núcleo de la estación cambia para siempre la comprensión humana sobre los viajes hiperespaciales.",
                primaryImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Elephants_Dream_Proog_and_Emo.jpg/640px-Elephants_Dream_Proog_and_Emo.jpg",
                backdropImageTag = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Elephants_Dream_Proog_and_Emo.jpg/1280px-Elephants_Dream_Proog_and_Emo.jpg",
                playbackPositionTicks = 0L
            )
        )
    }

    suspend fun populateDemoCatalog(
        database: AppDatabase,
        prefsRepo: UserPreferencesRepository
    ) {
        database.jellyfinDao().insertOrUpdateAll(getDemoEntities())
        prefsRepo.updateJellyfinConfig(
            serverUrl = DEMO_SERVER_URL,
            userId = DEMO_USER_ID,
            accessToken = DEMO_TOKEN,
            username = DEMO_USERNAME,
            password = ""
        )
        prefsRepo.updateJellyfinLastSync(System.currentTimeMillis().toString())
        GetHomeFeedUseCase.invalidateFeedCache()
        MediaRepository.invalidateServerCaches()
    }

    suspend fun clearDemoCatalog(database: AppDatabase) {
        database.jellyfinDao().deleteDemoItems()
        GetHomeFeedUseCase.invalidateFeedCache()
        MediaRepository.invalidateServerCaches()
    }
}
