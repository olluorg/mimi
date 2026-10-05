package org.ollu.mini.about

/** One recording the app plays, and who it came from. */
data class SoundCredit(
    val label:   String,
    val title:   String,
    val author:  String,
    val licence: String,
    val page:    String
)

/**
 * The animal voices and where they came from.
 *
 * Most of these are Creative Commons recordings whose licence asks for the
 * author to be named wherever the work is used. An app that plays them and
 * credits nobody is not using them on the terms they were given, so the list
 * is reachable from the adult menu rather than living in a file in the repo.
 *
 * Generated from catalog/_sounds/animals/sounds.json; a test fails if the two
 * drift apart.
 */
val ANIMAL_SOUND_CREDITS: List<SoundCredit> = listOf(
    SoundCredit(
        label   = "Медведь",
        title   = "Bear growl.ogg",
        author  = "Shizhao",
        licence = "CC BY 3.0",
        page    = "https://commons.wikimedia.org/wiki/File%3ABear_growl.ogg"
    ),
    SoundCredit(
        label   = "Кошка",
        title   = "Meow of a Siamese cat - freemaster2.wav",
        author  = "freemaster2",
        licence = "CC0",
        page    = "https://commons.wikimedia.org/wiki/File%3AMeow_of_a_Siamese_cat_-_freemaster2.wav"
    ),
    SoundCredit(
        label   = "Корова",
        title   = "Single Cow Moo.ogg",
        author  = "MichaeltheFox8621",
        licence = "CC BY-SA 4.0",
        page    = "https://commons.wikimedia.org/wiki/File%3ASingle_Cow_Moo.ogg"
    ),
    SoundCredit(
        label   = "Собака",
        title   = "Barking of a dog.ogg",
        author  = "Amada44",
        licence = "CC BY-SA 3.0",
        page    = "https://commons.wikimedia.org/wiki/File%3ABarking_of_a_dog.ogg"
    ),
    SoundCredit(
        label   = "Утка",
        title   = "Anas platyrhynchos - Mallard - XC62258.ogg",
        author  = "Jonathon Jongsma",
        licence = "CC BY-SA 3.0",
        page    = "https://commons.wikimedia.org/wiki/File%3AAnas_platyrhynchos_-_Mallard_-_XC62258.ogg"
    ),
    SoundCredit(
        label   = "Слон",
        title   = "Elephant voice - trumpeting.ogg",
        author  = "தகவலுழவன்",
        licence = "CC0",
        page    = "https://commons.wikimedia.org/wiki/File%3AElephant_voice_-_trumpeting.ogg"
    ),
    SoundCredit(
        label   = "Лягушка",
        title   = "Single Frog Croak.oga",
        author  = "MichaeltheFox8621",
        licence = "CC BY-SA 4.0",
        page    = "https://commons.wikimedia.org/wiki/File%3ASingle_Frog_Croak.oga"
    ),
    SoundCredit(
        label   = "Лошадь",
        title   = "Wiehern.ogg",
        author  = "Hü.",
        licence = "Public domain",
        page    = "https://commons.wikimedia.org/wiki/File%3AWiehern.ogg"
    ),
    SoundCredit(
        label   = "Лев",
        title   = "Lion raring-sound1TamilNadu178.ogg",
        author  = "த*உழவன்",
        licence = "Public domain",
        page    = "https://commons.wikimedia.org/wiki/File%3ALion_raring-sound1TamilNadu178.ogg"
    ),
    SoundCredit(
        label   = "Свинья",
        title   = "Pig grunt - Erdie.ogg",
        author  = "erdie",
        licence = "CC BY 3.0",
        page    = "https://commons.wikimedia.org/wiki/File%3APig_grunt_-_Erdie.ogg"
    ),
    SoundCredit(
        label   = "Петух",
        title   = "Rooster crowing.ogg",
        author  = "Filo gèn'",
        licence = "CC BY-SA 4.0",
        page    = "https://commons.wikimedia.org/wiki/File%3ARooster_crowing.ogg"
    ),
    SoundCredit(
        label   = "Овца",
        title   = "Mudchute sheep 1.ogg",
        author  = "Secretlondon",
        licence = "CC BY-SA 3.0",
        page    = "https://commons.wikimedia.org/wiki/File%3AMudchute_sheep_1.ogg"
    ),
    SoundCredit(
        label   = "Волк",
        title   = "Jem howls.ogg",
        author  = "amethysta",
        licence = "Public domain",
        page    = "https://commons.wikimedia.org/wiki/File%3AJem_howls.ogg"
    )
)
