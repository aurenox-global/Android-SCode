★★★★★★★★★★★★★★★★★★★★★★★
★★★  Android SCode v6.3.0 ★★★
★★★★★★★★★★★★★★★★★★★★★★★

This is an advanced Android SCode Mod that was re-modded from the Android SCode Revolution by Agus Jcoderz.
Please give credit when you re-mod or share. Our Main team includes:

- Aldi Sayuti
- Hilal Saif
- Mike Anderson (Hey! Studios)
- Jbk0
- Hasrat

Contributors & Special Thanks:

- IndoSW                (for Direct Code Editor support)
- Agus JCoderZ          (for the base mod, Android SCode Revolution)
- Auwal Emptyset        (for library downloading source)
- Ani1nonly
- Dava
- Ilyasse Salama        (for the new About Android SCode activity)
- Zarzo
- AlucardTN             (for some miscellaneous stuff)
- tyron                 (for .swb file Intent filter)
- Iyxan23               (for some miscellaneous stuff)
- Aliveness             (for an improved pretty-print & replaceable generated files)
- khaled                (for the HUGE Android 11+ storage performance fix & other fixes)
- Zirus                 (for an improved project backup dialog)
- Pranav                (for an improved MainActivity)

Make sure to look in-app for an up-to-date list of our team.

DISCLAIMER: This mod was not meant for any harmful purposes, such as harming Android SCode; Quite the opposite actually: It was made to keep Android SCode alive.
Please use it on your own discretion and buy the Android SCode Premium subscription every month to support the original Android SCode team, even if you don't use it. 

We love Android SCode very much, and we are grateful to Android SCode's developers for making such an amazing app,
but unfortunately, we haven't received updates for a long time.
That's why we decided to keep Android SCode alive by making this mod, plus we don't demand any money - It's completely free :)
Also, starting with version 6.4.0 of the Mod, we've made some source code open source! You can check it out at https://github.com/aurenox-global/Android-SCode,
and if you want to contribute, go fork it and open a pull request! We'd love to see new amazing features getting added to Android SCode :D

★ The change log can be found in the mod's Main Drawer.

**** FOR MODDERS ****

I made this part specifically for my fellow modders who want to contribute to Android SCode's development. Think of it as a little documentation :)

» DEX info

   classes.dex  -> Contains most of Android SCode's classes, including the packages a.a.a and com.besome.sketch.

   classes2.dex -> Nothing exciting, only libraries such as AndroidX, Glide, AdMob, ProGuard, StringFog, ASM etc.

   classes3.dex -> More libraries, including Gson, Firebase, kellinwood ZipSigner, okhttp3, etc.

   classes4.dex -> Contains some compiler libraries including Dx, ecj, MultiDex, etc. but also FilePicker, CodeEditor, etc.

   classes5.dex -> Contains all modders' classes: 
       Agus JCoderZ        -> mod.agus.jcoderz
       Aldi Sayuti         -> dev.aldi.sayuti
       Hilal Saif          -> mod.hilal.saif
       Hey! Studios DEV    -> mod.hey.studios
       Ilyasse Salama      -> mod.ilyasse
       IndoSW              -> id.indosw.mod
       Jbk0                -> mod.jbk
       tyron               -> mod.tyron

     and, Android SCode's most important classes that are modified for the Mod, including:
       Fx -> Responsible for generating the code of each block. Cannot be decompiled properly due to its HUGE size, you may want to edit this one by smali.
       Jx -> Responsible for generating the source code of activities.
       Lx -> Responsible for generating source codes of stuff such as listeners etc.
       Ox -> Responsible for generating the XML source of layouts.

   classes6.dex -> This one is all about the compilation. Contains ProGuard, StringFog handler classes/activities, and:
       Dp -> Responsible for the whole compilation process of projects. Was modified to fix MultiDex bug and to add library support.
       yq -> Organizes Android SCode projects' file paths.
       
   classes7.dex -> Contains both ProGuard and d8/r8
   
   classes8.dex -> Contains some dependencies of bundletool, this is the only DEX supposed to be of version 038!
   
   classes9.dex -> Contains bundletool & its dependencies

