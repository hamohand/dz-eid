import 'package:dz_eid/dz_eid.dart';
import 'package:flutter/material.dart';

import 'key_screen.dart';
import 'qr_scanner_screen.dart';

import 'package:cryptography_flutter/cryptography_flutter.dart';

void main() {
  FlutterCryptography.enable();
  runApp(const DzEidDemoApp());
}

/// Application de démonstration du plugin dz_eid (lecture de la CNIe algérienne par NFC).
class DzEidDemoApp extends StatelessWidget {
  const DzEidDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'dz-eid',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: const Color(0xFF006233), useMaterial3: true),
      home: const HomeScreen(),
    );
  }
}

/// Accueil : état du NFC et accès à la lecture.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  NfcStatus? _status;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Retour des paramètres NFC : on relit l'état.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    try {
      final s = await DzEid.getNfcStatus();
      if (mounted) {
        setState(() {
          _status = s;
          _error = null;
        });
      }
    } on DzEidException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('dz-eid : lecture CNIe')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Icon(Icons.badge_outlined, size: 88, color: theme.colorScheme.primary),
          const SizedBox(height: 12),
          Text('Carte d\'identité électronique algérienne',
              textAlign: TextAlign.center, style: theme.textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text(
            'Lecture sécurisée de la puce par NFC : identité, photo, signature et contrôle d\'authenticité. '
            'Tout est traité sur le téléphone, rien n\'est envoyé sur Internet.',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          _NfcStatusCard(status: _status, error: _error, onRefresh: _refresh),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _status == NfcStatus.available
                ? () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const KeyScreen()))
                : null,
            icon: const Icon(Icons.contactless),
            label: const Padding(padding: EdgeInsets.all(12), child: Text('Lire une carte')),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _status == NfcStatus.available
                ? () async {
                    
                    final qrPayload = await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const QrScannerScreen()));
                    if (qrPayload != null && mounted) {
                      Navigator.of(context).push(MaterialPageRoute(builder: (_) => KeyScreen(qrPayload: qrPayload)));
                    }
                  }
                : null,
            icon: const Icon(Icons.qr_code_scanner),
            label: const Padding(padding: EdgeInsets.all(12), child: Text('Relais Web (Scan QR Code)')),
          ),
        ],
      ),
    );
  }
}

class _NfcStatusCard extends StatelessWidget {
  const _NfcStatusCard({required this.status, required this.error, required this.onRefresh});

  final NfcStatus? status;
  final String? error;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final (icon, color, text) = switch (status) {
      NfcStatus.available => (Icons.check_circle, Colors.green, 'NFC activé : prêt à lire.'),
      NfcStatus.disabled => (Icons.warning_amber, Colors.orange, 'Le NFC est désactivé.'),
      NfcStatus.unavailable => (Icons.block, Colors.red, 'Ce téléphone ne dispose pas du NFC.'),
      null => (Icons.hourglass_empty, Colors.grey, error ?? 'Vérification du NFC…'),
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(children: [
              Icon(icon, color: color),
              const SizedBox(width: 12),
              Expanded(child: Text(text)),
              IconButton(onPressed: onRefresh, icon: const Icon(Icons.refresh), tooltip: 'Actualiser'),
            ]),
            if (status == NfcStatus.disabled)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(onPressed: DzEid.openNfcSettings, child: const Text('Activer le NFC')),
              ),
          ],
        ),
      ),
    );
  }
}
