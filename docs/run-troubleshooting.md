If the Error Happens Again

If you encounter the same ClassNotFoundException error in the future:

1. Clean and refresh dependencies:                                                                    
   ./gradlew clean :cookbook:build --refresh-dependencies
2. If using an IDE (IntelliJ IDEA, Eclipse, etc.):                                                    
   - Refresh Gradle project (IntelliJ: View → Tool Windows → Gradle → Reload)                          
   - Invalidate caches and restart (IntelliJ: File → Invalidate Caches)                                
   - Re-import the project
3. Clear Gradle cache (nuclear option):                                                               
   rm -rf ~/.gradle/caches                                                                               
   ./gradlew clean build            