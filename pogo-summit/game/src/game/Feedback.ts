import type { SimEvent } from '../sim/events';
import type { PogoAudioDirector } from '../audio/system/PogoAudioDirector';
import type { HapticManager } from '../haptics/HapticManager';
import type { Hud } from '../ui/Hud';
import { t } from '../ui/i18n';

/**
 * Routes simulation events to audio + haptics + HUD. This table is the single source of truth for "what feels like what" for
 * haptics and HUD; the *sound* of each event is decided by `PogoAudioDirector` (events → audio events → variants, buses, limits).
 */
export class Feedback {
  constructor(private readonly audio: PogoAudioDirector, private readonly haptics: HapticManager, private readonly hud: Hud | null) {}

  handle(events: readonly SimEvent[]): void {
    this.audio.handle(events);
    for (const e of events) {
      switch (e.type) {
        case 'charge_start': this.haptics.trigger('ui', 0.5); break;
        case 'launch': this.haptics.trigger('jump', e.intensity); break;
        case 'land': this.haptics.trigger('landing', e.intensity); break;
        case 'hard_impact': this.haptics.trigger('hardImpact', 1); break;
        case 'bounce': this.haptics.trigger('bounce', e.intensity); break;
        case 'wall_hit': if (e.intensity > 0.3) this.haptics.trigger('landing', e.intensity); break;
        case 'boost_armed': this.haptics.trigger('boost', 0.4); this.hud?.toast(t('boostReady'), 1100); break;
        case 'boost': this.haptics.trigger('boost', 1); break;
        case 'boost_pad': this.haptics.trigger('boost', 0.7); break;
        case 'hazard': this.haptics.trigger('hazard', 1); this.hud?.flashDanger(); this.hud?.toast(t('hazardHit'), 900); break;
        case 'fall': this.hud?.toast(t('fell'), 1200); break;
        case 'goal': this.haptics.trigger('goal', 1); break;
        default: break;
      }
    }
  }
}
