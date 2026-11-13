import { Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** Focuses an input that appears while the page is in use (the autofocus attribute only works on page load). */
@Directive({ selector: '[appAutofocus]' })
export class Autofocus {
  constructor() {
    const element = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
    afterNextRender(() => {
      element.focus();
      element.select?.();
    });
  }
}
