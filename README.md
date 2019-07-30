# Actuator
A series of transactions for blockchain.

## Dependencies

The lastest version is **1.0.0**.

### GRADLE

Step 1. Add the JitPack repository in your root build.gradle at the end of repositories:
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
	  }
}
```
Step 2. Add the dependency. 
```
dependencies {
    implementation 'com.github.tronprotocol:actuator:${version}'
}
```

### MAVEN

Step 1. Add the JitPack repository to your build file.

```
<repositories>
	  <repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
	  </repository>
</repositories>

```
Step 2. Add the dependency.
```
<dependency>
    <groupId>com.github.tronprotocol</groupId>
	  <artifactId>actuator</artifactId>
	  <version>${version}</version>
</dependency>
	
```
