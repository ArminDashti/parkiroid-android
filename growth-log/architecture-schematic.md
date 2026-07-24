# Growth log — architecture (schematic)

```
PC (install-on-phone-directly.ps1 | scripts/install-apk.sh)
  --> adb --> USB cable --> Android device (com.dogan)
         | optionally
         v
   scripts/export-apk.* --> Gradle assemble --> APK file
```
