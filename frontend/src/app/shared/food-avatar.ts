import { Component, computed, input } from '@angular/core';

import { UNKNOWN_FOOD, foodHue } from './food';

/**
 * The food's emoji on a tinted disc. Around it, a ring shows how much of the shelf life is left, coloured by
 * expiry status: cool when fresh, warm when it should be eaten soon.
 */
@Component({
  selector: 'app-food-avatar',
  template: `
    <div
      class="avatar"
      [style.--hue]="hue()"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [class]="'avatar status-' + (status() ?? 'none').toLowerCase()"
    >
      @if (freshness() !== null) {
        <svg class="ring" viewBox="0 0 100 100" aria-hidden="true">
          <circle class="track" cx="50" cy="50" r="46" />
          <circle
            class="value"
            cx="50"
            cy="50"
            r="46"
            pathLength="100"
            [attr.stroke-dasharray]="ringLength() + ' 100'"
            transform="rotate(-90 50 50)"
          />
        </svg>
      }
      <span class="disc">
        @if (photo(); as photo) {
          <img class="photo" [src]="photo" alt="" />
        } @else {
          <span class="emoji" [style.font-size.px]="size() * 0.42">{{ emoji() || unknown }}</span>
        }
      </span>
    </div>
  `,
  styles: `
    :host {
      display: inline-block;
      flex: none;
    }
    .avatar {
      position: relative;
      display: grid;
      place-items: center;
    }
    .ring {
      position: absolute;
      inset: 0;
      overflow: visible;
    }
    .track {
      fill: none;
      stroke: var(--hairline);
      stroke-width: 6;
    }
    .value {
      fill: none;
      stroke: var(--status, var(--mat-sys-primary));
      stroke-width: 6;
      stroke-linecap: round;
      transition: stroke-dasharray 0.8s var(--ease-out);
    }
    .disc {
      position: absolute;
      inset: 11%;
      display: grid;
      place-items: center;
      overflow: hidden;
      border-radius: 50%;
      background: light-dark(hsl(var(--hue) 30% 93%), hsl(var(--hue) 10% 24%));
      box-shadow: inset 0 -5px 10px -6px
        light-dark(hsl(var(--hue) 20% 70% / 0.35), rgb(0 0 0 / 0.35));
    }
    .photo {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      animation: fade-in 0.3s ease-out both;
    }
    @keyframes fade-in {
      from {
        opacity: 0;
      }
    }
    .emoji {
      line-height: 1;
      filter: drop-shadow(0 2px 3px rgb(0 0 0 / 0.18));
    }
  `,
})
export class FoodAvatar {
  /** The emoji of the food's category. */
  readonly emoji = input<string | null | undefined>();
  readonly status = input<string | null>(null);
  /** 0..1 or null to hide the ring. */
  readonly freshness = input<number | null>(null);
  readonly size = input(56);
  /** URL of a photo of the food; shown instead of the emoji. */
  readonly photo = input<string | null>(null);

  protected readonly unknown = UNKNOWN_FOOD;
  protected readonly hue = computed(() => foodHue(this.emoji()));
  protected readonly ringLength = computed(() => {
    const value = this.status() === 'EXPIRED' ? 1 : (this.freshness() ?? 0);
    return Math.max(value * 100, 2).toFixed(1);
  });
}
