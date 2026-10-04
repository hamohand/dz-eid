# dz_eid

Plugin Flutter de lecture des cartes d'identité électroniques algériennes (CNIe) par NFC, avec scan de la MRZ par la caméra. Android 8.0 (API 26) minimum.

Guide complet : [docs/integration-flutter.md](../../docs/integration-flutter.md).

```dart
final scan = await DzEid.scanMrz();
if (scan == null) return;
final id = await DzEid.readCard(accessKey: scan.toAccessKey(), onProgress: (p) => print(p.message));
print(id.holder.lastNameArabic);
```
