import { Component, computed, input } from '@angular/core';

/**
 * Initials on a gradient that is derived from the name, so everyone keeps their colour.
 */
@Component({
  selector: 'app-initials-avatar',
  template: `
    <span
      class="avatar"
      [class.square]="square()"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.font-size.px]="size() * 0.4"
      [style.background]="background()"
      aria-hidden="true"
      >{{ initials() }}</span
    >
  `,
  styles: `
    :host {
      display: inline-block;
      flex: none;
    }
    .avatar {
      display: grid;
      place-items: center;
      border-radius: 50%;
      color: #fff;
      font-family: var(--font-display);
      font-weight: 600;
      letter-spacing: 0.02em;
      box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.2);
    }
    .square {
      border-radius: 30%;
    }
  `,
})
export class InitialsAvatar {
  readonly name = input.required<string>();
  readonly size = input(40);
  readonly square = input(false);

  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter((word) => /\p{L}|\p{N}/u.test(word))
      .slice(0, 2)
      .map((word) => [...word][0].toUpperCase())
      .join(''),
  );

  protected readonly background = computed(() => {
    let hash = 0;
    for (const char of this.name()) {
      hash = (hash * 31 + char.codePointAt(0)!) | 0;
    }
    const [from, to] = TONES[Math.abs(hash) % TONES.length];
    return `linear-gradient(135deg, ${from}, ${to})`;
  });
}

/** Muted tones that sit well next to the steel-blue accent. */
const TONES: [string, string][] = [
  ['#5f7f94', '#4a6a80'], // steel
  ['#6f8a77', '#5a7462'], // sage
  ['#a08a6a', '#887354'], // sand
  ['#5f8a8a', '#4b7272'], // teal
  ['#7a8290', '#636b79'], // slate
  ['#9a7f78', '#836862'], // clay
];
