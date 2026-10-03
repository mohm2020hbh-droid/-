import type { SimEvent } from '../sim/events';
import type { SFXManager } from '../audio/SFXManager';
import { LANDING_BY_MATERIAL, SFX } from '../audio/AudioEvents';
import type { HapticManager } from '../haptics/HapticManager';
import type { Hud } from '../ui/Hud';
import { t } from '../ui/i18n';

/** Routes simulation events to audio + haptics + HUD. This table is the single source of truth for "what feels like what". */
export class Feedback {
  constructor(private readonly sfx: SFXManager, private readonly haptics: HapticManager, private readonly hud: Hud | null) {}

  handle(events: readonly SimEvent[]): void {
    for (const e of events) {
      switch (e.type) {
        case 'charge_start': this.sfx.play(SFX.chargeStart); this.haptics.trigger('ui', 0.5); break;
        case 'launch':
          this.sfx.play(SFX.launch, { intensity: e.intensity });
          if (Math.random() < 0.6) this.sfx.play(SFX.voiceHup, { intensity: e.intensity });
          this.haptics.trigger('jump', e.intensity); break;
        case 'land':
          this.sfx.play(LANDING_BY_MATERIAL[e.material ?? 'grass'] ?? SFX.landSoft, { intensity: 0.25 + e.intensity * 0.75 });
          this.haptics.trigger('landing', e.intensity); break;
        case 'hard_impact': this.sfx.play(SFX.hardImpact); this.haptics.trigger('hardImpact', 1); break;
        case 'bounce': this.sfx.play(SFX.bounce, { intensity: e.intensity }); this.haptics.trigger('bounce', e.intensity); break;
        case 'wall_hit': this.sfx.play(SFX.wallHit, { intensity: e.intensity }); if (e.intensity > 0.3) this.haptics.trigger('landing', e.intensity); break;
        case 'slide': this.sfx.play(SFX.slide); break;
        case 'boost_armed': this.sfx.play(SFX.boostArmed); this.haptics.trigger('boost', 0.4); this.hud?.toast(t('boostReady'), 1100); break;
        case 'boost': this.sfx.play(SFX.boost); this.haptics.trigger('boost', 1); break;
        case 'boost_pad': this.sfx.play(SFX.boostPad); this.haptics.trigger('boost', 0.7); break;
        case 'hazard': this.sfx.play(SFX.hazard); this.sfx.play(SFX.voiceOuch); this.haptics.trigger('hazard', 1); this.hud?.flashDanger(); this.hud?.toast(t('hazardHit'), 900); break;
        case 'fall': this.sfx.play(SFX.fall); this.hud?.toast(t('fell'), 1200); break;
        case 'respawn': this.sfx.play(SFX.respawn); break;
        case 'goal': this.sfx.play(SFX.goal); this.sfx.play(SFX.voiceYay); this.haptics.trigger('goal', 1); break;
      }
    }
  }
}
