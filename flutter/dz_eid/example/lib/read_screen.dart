import 'package:dz_eid/dz_eid.dart';
import 'package:flutter/material.dart';

import 'result_screen.dart';

/// Lecture : attente de la carte, progression, erreurs.
class ReadScreen extends StatefulWidget {
  const ReadScreen({super.key, required this.accessKey});

  final AccessKey accessKey;

  @override
  State<ReadScreen> createState() => _ReadScreenState();
}

class _ReadScreenState extends State<ReadScreen> {
  ReadProgress? _progress;
  DzEidException? _error;
  bool _reading = false;
  final _stopwatch = Stopwatch();

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void dispose() {
    if (_reading) DzEid.cancel();
    super.dispose();
  }

  Future<void> _start() async {
    setState(() {
      _reading = true;
      _error = null;
      _progress = null;
    });
    _stopwatch.reset();
    try {
      final record = await DzEid.readCard(
        accessKey: widget.accessKey,
        onProgress: (p) {
          // Chronomètre : de la détection de la carte à la fin de la lecture
          if (p.waitingForCard) {
            _stopwatch
              ..stop()
              ..reset();
          } else if (!_stopwatch.isRunning) {
            _stopwatch.start();
          }
          if (mounted) setState(() => _progress = p);
        },
      );
      _stopwatch.stop();
      _reading = false;
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(
        builder: (_) => ResultScreen(record: record, duration: _stopwatch.elapsed),
      ));
    } on DzEidException catch (e) {
      _reading = false;
      if (e.code == 'CANCELLED' || !mounted) return;
      setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Lecture de la puce')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: _error != null ? _buildError(theme, _error!) : _buildProgress(theme),
      ),
    );
  }

  Widget _buildProgress(ThemeData theme) {
    final p = _progress;
    final waiting = p == null || p.waitingForCard;
    final percent = p?.percent ?? 0;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(waiting ? Icons.contactless_outlined : Icons.sync, size: 110, color: theme.colorScheme.primary),
        const SizedBox(height: 24),
        Text(
          waiting ? 'Approchez la carte' : 'Ne bougez plus la carte',
          textAlign: TextAlign.center,
          style: theme.textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        Text(
          p?.message ?? 'Posez la carte à plat contre le dos du téléphone, au centre, et maintenez-la immobile.',
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 28),
        LinearProgressIndicator(value: waiting ? null : percent / 100),
        if (!waiting) ...[
          const SizedBox(height: 8),
          Text('$percent %', textAlign: TextAlign.center),
        ],
        const SizedBox(height: 40),
        OutlinedButton(
          onPressed: () async {
            await DzEid.cancel();
            if (mounted) Navigator.of(context).pop();
          },
          child: const Text('Annuler'),
        ),
      ],
    );
  }

  Widget _buildError(ThemeData theme, DzEidException e) {
    final hint = switch (e.code) {
      'ACCESS_DENIED' => 'Vérifiez le numéro de la carte et les dates saisies (ou scannez à nouveau le dos de la carte).',
      'NO_CARD' => 'L\'antenne NFC se trouve généralement au centre du dos du téléphone. Retirez une coque épaisse si besoin.',
      'CARD_LOST' => 'Gardez la carte immobile contre le téléphone pendant toute la lecture (2 à 4 secondes).',
      'NFC_DISABLED' => 'Activez le NFC dans les paramètres du téléphone.',
      _ => null,
    };
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(Icons.error_outline, size: 96, color: theme.colorScheme.error),
        const SizedBox(height: 20),
        Text(e.message, textAlign: TextAlign.center, style: theme.textTheme.titleMedium),
        if (hint != null) ...[
          const SizedBox(height: 12),
          Text(hint, textAlign: TextAlign.center),
        ],
        const SizedBox(height: 8),
        Text('Code : ${e.code}', textAlign: TextAlign.center, style: theme.textTheme.bodySmall),
        const SizedBox(height: 32),
        if (e.code == 'NFC_DISABLED')
          FilledButton(onPressed: DzEid.openNfcSettings, child: const Text('Ouvrir les paramètres NFC'))
        else
          FilledButton(onPressed: _start, child: const Text('Réessayer')),
        const SizedBox(height: 12),
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Retour')),
      ],
    );
  }
}
